;; Copyright © 2014, JUXT LTD. All Rights Reserved.

(ns bolt.user.login
  (:require
   [clojure.tools.logging :refer :all]
   [clojure.string :as string]
   [bolt.user.protocols :as p]
   [bolt.authentication.protocols :refer (RequestAuthenticator AuthenticationHandshake)]
   [bolt.session :refer (start-session! stop-session! session-data)]
   [bolt.session.protocols :refer (SessionLifecycle SessionData)]
   [bolt.user :refer (find-user authenticate-user update-user! hash-password)]
   [bolt.util :refer (as-query-string uri-with-qs Request wrap-schema-validation keywordize-form)]
   [bidi.bidi :refer (RouteProvider tag)]
   [modular.bidi :refer (path-for)]
   [ring.util.response :refer (redirect redirect-after-post)]
   [ring.middleware.params :refer (params-request)]
   [plumbing.core :refer (<-)]
   [com.stuartsierra.component :refer (Lifecycle using)]
   [schema.core :as s]
   [yada.yada :refer (yada)]
   [modular.component.co-dependency :refer (co-using)]
   [modular.component.co-dependency.schema :refer (co-dep)])
  (:import [java.net URLEncoder URLDecoder]
           [modular.bidi Router]))

(defn email? [s]
  (re-matches #".+@.+" s))

(defn get-login-post-path [component redirect onfail]
  (str
   (path-for @(:*router component) (keyword (:tag-ns component) "login"))
   (as-query-string {"redirect" (URLEncoder/encode redirect)
                     "onfail" (URLEncoder/encode onfail)})))

(defn get-logout-post-path [component redirect]
  (str
   (path-for @(:*router component) (keyword (:tag-ns component) "logout"))
   (as-query-string {"redirect" (URLEncoder/encode redirect)})))

(defn get-set-password-post-path [component identity redirect onfail]
  (str
   (path-for @(:*router component) (keyword (:tag-ns component) "set-password") :identity identity)
   (as-query-string {"redirect" (URLEncoder/encode redirect)
                     "onfail" (URLEncoder/encode onfail)})))

(defn make-routes [{:keys [user-store user-authenticator session password-hasher tag-ns]}]
  {"/login" (-> nil
                (yada
                 :parameters {:post {:form {(s/required-key :identity) s/Str
                                            (s/required-key :password) s/Str
                                            s/Keyword s/Str}
                                     :query {:redirect s/Str :onfail s/Str}}}
                 :post! (fn [{{:keys [identity password redirect onfail] :as parameters} :parameters :as ctx}]
                          (let [user (find-user user-store identity)
                                authentication (when user
                                                 (authenticate-user user-authenticator user
                                                                    {:password password}))]
                            (if (and user authentication)
                              ;; Login successful!
                              (start-session! session (redirect-after-post redirect)
                                              {:bolt/identity identity :bolt/user user})
                              ;; Login failed!
                              (redirect-after-post onfail)))))
                wrap-schema-validation
                (tag (keyword tag-ns "login")))

   ;; Logout is most certainly NOT a GET (not something you want to
   ;; cache and accidentally leave yourself logged in)
   "/logout" (-> nil
                 (yada
                  :parameters {:post {:query {:redirect s/Str}}}
                  :post! (fn [{{:keys [redirect] :as parameters} :parameters req :request :as ctx}]
                           (stop-session! session (redirect-after-post redirect) (session-data session req))))
                 wrap-schema-validation
                 (tag (keyword tag-ns "logout")))

   ["/passwords/" [#"[\w\.\@\-\_\%]+" :identity]]
   (-> nil
       (yada
        :parameters {:post {:path {:identity s/Str}
                            :form {:password s/Str}
                            :query {:redirect s/Str :onfail s/Str}}}

        ;; This is a POST to ensure that it can be called via AJAX and traditional HTML forms
        :post! (fn [{{:keys [identity password redirect onfail] :as parameters} :parameters
                    req :request
                    :as ctx}]

                 (let [ ;; We must decode identity. A future version of bidi may do this for us.
                       ;; Awkward but (currently) necessary
                       ;; A general login solution must be more general that this
                       path-identity (URLDecoder/decode identity)]

                   ;; The identity MUST match that of the session data
                   (let [session-data (session-data session req)
                         real-identity (-> session-data :bolt/identity)]

                     ;; It's not too late to send a 400 - we can do that via an exception
                     (when (not= real-identity path-identity)
                       (throw (ex-info "TODO: Return a 400" {:real-identity real-identity
                                                             :path-identity path-identity})))

                     (let [user (find-user user-store path-identity)]

                       ;; TODO: Implement password policies
                       (cond
                         true           ; password ok
                         (do (update-user! user-store path-identity
                                           (assoc-in user [:password] (hash-password password-hasher password)))
                             (redirect-after-post redirect))
                         :otherwise (redirect-after-post onfail)))))))
       wrap-schema-validation
       (tag (keyword tag-ns "set-password")))})

(s/defrecord Login
    [user-store :- (s/protocol p/UserStore)
     user-authenticator :- (s/protocol p/UserAuthenticator)
     password-hasher :- (s/protocol p/UserPasswordHasher) ; for reset of passwords, possibly the same component as user-authenticator
     session :- (s/both (s/protocol SessionData) (s/protocol SessionLifecycle))
     uri-context :- s/Str
     tag-ns :- s/Str
     *router :- (co-dep Router)]

  ;; TODO: This is now deprecated, now that Login only does post
  ;; processing and doesn't 'know' where the login form is. An
  ;; alternative implementation will be required.
  AuthenticationHandshake
  (initiate-authentication-handshake
   [component req]
   (assert (:routes @*router))
   (if-let [p (path-for @*router (keyword tag-ns "login-form"))]
     (let [loc (str p (as-query-string {"post_login_redirect" (URLEncoder/encode (uri-with-qs req))}))]
       (debugf "Redirecting to %s" loc)
       (redirect loc))
     (throw (ex-info "No path to login form" {}))))

  RequestAuthenticator
  (authenticate [component req]
                (session-data session req))

  RouteProvider
  (routes [component]
          [uri-context (make-routes component)]))

(defn new-login [& {:as opts}]
  (-> (map->Login (merge {:uri-context "" :tag-ns "bolt.user.login"} opts))
      (using [:user-store :password-hasher :user-authenticator :session])
      (co-using [:router])))

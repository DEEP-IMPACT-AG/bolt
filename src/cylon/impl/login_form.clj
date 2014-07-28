;; Copyright © 2014, JUXT LTD. All Rights Reserved.

(ns cylon.impl.login-form
  (:require
   [clojure.tools.logging :refer :all]
   [com.stuartsierra.component :as component]
   [schema.core :as s]
   [ring.middleware.cookies :refer (wrap-cookies)]
   [ring.middleware.params :refer (wrap-params)]
   [hiccup.core :refer (html)]
   [bidi.bidi :refer (path-for ->WrapMiddleware)]
   [modular.bidi :refer (WebService)]
   [cylon.user :refer (UserDomain verify-user)]
   [cylon.session :refer (SessionStore start-session! end-session!)]))

(defprotocol LoginFormRenderer
  (render-login-form [_ request attrs]))

(defrecord PlainLoginFormRenderer []
  LoginFormRenderer
  (render-login-form [_ request {:keys [requested-uri action login-status fields]}]
    (html
     [:div
      [:form {:method "POST"
              :style "border: 1px dotted #555; padding: 10pt"
              :action action}

       [:h2 "Please sign in"]

       (when login-status
         [:div
          [:p
           (case login-status
             :failed [:span [:strong "Failed: "] "Please check login credentials and try again or " [:a.alert-link {:href "#"} "reset your password"] "."])]])

       (for [{:keys [id label name type value placeholder required autofocus]} fields]
         [:div
          [:label {:for id} label]
          [:input (merge
                   {:name name :type type :value value}
                   (when placeholder {:placeholder placeholder})
                   (when required {:required required})
                   (when autofocus {:autofocus autofocus}))]])

       (when (not-empty requested-uri)
         [:input {:type "hidden" :name :requested-uri :value requested-uri}])

       [:input {:type "submit" :value "Sign in"}]

       [:p
        [:a {:href "#"} "Reset password"]]
       ]])))

(defn new-plain-login-form-renderer []
  (->PlainLoginFormRenderer))

;; TODO Because we're using email, not username - need to make this configurable.

(defn new-login-post-handler [& {:keys [user-domain session-store uid] :as opts}]
  (s/validate {:user-domain (s/protocol UserDomain)
               :session-store (s/protocol SessionStore)
               :uid s/Str}
              opts)
  (fn [{params :form-params
        routes :modular.bidi/routes}]

    (let [id (get params uid) password (get params "password")]

      (if (and id
               (not-empty id)
               (verify-user user-domain (.trim id) password))

        {:status 302
         :headers {"Location" (or (get params "requested-uri") "/")} ; "/" can be parameterized (TODO)
         :cookies {"session-id" (start-session! session-store id)
                   "requested-uri" ""}}

        ;; Return back to login form
        {:status 302
         :headers {"Location" (path-for routes :login)}
         :cookies {"login-status" "failed"
                   "uid" id}}))))

(defn new-logout-handler [session-store]
  (fn [{:keys [cookies]}]
    (end-session!
     session-store
     (:value (get cookies "session-id")))
    {:status 302 :headers {"Location" "/"}}))

(defrecord LoginForm [uri-context renderer middleware fields uid requested-uri]
  WebService
  (request-handlers [this]
    {:login
     (let [f (fn [{{{login-status :value} "login-status"
                    {uid-value :value} "uid"} :cookies
                    routes :modular.bidi/routes :as request}]
               {:status 200
                :body (render-login-form
                       renderer
                       request
                       (merge
                        {:action (path-for routes :process-login)
                         :fields (if uid-value
                                   (->> fields
                                        (map #(if (= (:name %) uid) (assoc % :value uid-value) %))
                                        (map #(if (= (:name %) "password") (assoc % :autofocus true) %)))
                                   (->> fields
                                        (map #(if (= (:name %) uid) (assoc % :autofocus true) %))))}
                        (when (not-empty requested-uri) {:requested-uri requested-uri})
                        (when (not-empty login-status) {:login-status (keyword login-status)})))
                :cookies {"login-status" ""
                          "uid" ""}})]
       (wrap-cookies (if middleware (middleware f) f)))

     :process-login
     (-> (apply new-login-post-handler
                (apply concat (seq (select-keys this [:user-domain :session-store :uid]))))
         wrap-params wrap-cookies)

     :logout
     (-> (new-logout-handler (:session-store this))
         wrap-cookies)})

  (routes [this]
    ["" {"/login" {:get :login, :post :process-login}
         "/logout" {:get :logout}}])

  (uri-context [this] uri-context))

(def new-login-form-schema
  {(s/optional-key :uri-context) s/Str
   (s/optional-key :renderer) (s/protocol LoginFormRenderer)
   (s/optional-key :middleware) (s/=> 1)
   (s/required-key :uid) s/Str
   (s/required-key :fields) [{(s/optional-key :id) s/Str
                              (s/required-key :name) s/Str
                              (s/required-key :type) s/Str
                              (s/optional-key :label) s/Str
                              (s/optional-key :placeholder) s/Str
                              (s/optional-key :required) s/Bool}]
   (s/optional-key :requested-uri) s/Str})

(defn new-login-form [& {:as opts}]
  (component/using
   (->> opts
        (merge {:uri-context ""
                :requested-uri "/"
                ;; If you don't provide a renderer, one will be provided for you
                :renderer (->PlainLoginFormRenderer)
                :fields
                [{:id "username" :name "username" :type "input" :label "Username"}
                 {:id "password" :name "password" :type "password" :label "Password"}]
                :uid "username"})
        (s/validate new-login-form-schema)
        map->LoginForm)
   [:user-domain :session-store]))

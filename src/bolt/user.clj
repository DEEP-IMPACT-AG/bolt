;; Copyright © 2014, JUXT LTD. All Rights Reserved.

(ns bolt.user
  (:require
   [bolt.user.protocols :as p]
   [bolt.util :refer (Request)]
   [schema.core :as s]
   [modular.email :refer (EmailAddress EmailMessage)]
   ))

;; UserStore API

(s/defschema User "A user"
  {s/Keyword s/Any})

(s/defn check-create-user
  [component :- (s/protocol p/UserStore)
   user :- User]
  (p/check-create-user component user))

(s/defn create-user!
  [component :- (s/protocol p/UserStore)
   user :- User]
  (p/create-user! component user))

(s/defn find-user :- (s/maybe User)
  [component :- (s/protocol p/UserStore)
   id :- s/Str]
  (p/find-user component id))

(s/defn update-user! :- nil
  [component :- (s/protocol p/UserStore)
   id :- s/Str
   user :- User]
  (p/update-user! component id user))

(s/defn delete-user! :- nil
  [component :- (s/protocol p/UserStore)
   id :- s/Str]
  (p/delete-user! component id))

(s/defn verify-email! :- nil
  [component :- (s/protocol p/UserStore)
   email :- s/Str]
  (p/verify-email! component email))

;; UserAuthenticator API

(s/defn authenticate-user :- s/Any
  [component :- (s/protocol p/UserAuthenticator)
   user :- User
   evidence :- {s/Keyword s/Str}]
  (p/authenticate-user component user evidence))

(s/defn hash-password :- s/Str
  [component :- (s/protocol p/UserPasswordHasher)
   password :- s/Str]
  (p/hash-password component password))

;; Login form renderer API

#_(s/defschema FormField
  {:name s/Str
   (s/optional-key :label) s/Str
   (s/optional-key :placeholder) s/Str
   (s/optional-key :type) s/Str
   (s/optional-key :value) s/Str})

(s/defschema Form
  {:method s/Keyword
   :action s/Str})

;; TODO When schema validating requests, with {:headers s/Any} I get the following :-
  ;;
  ;; java.lang.ClassCastException: java.lang.Class cannot be cast to clojure.lang.IFn
  ;;       at aleph.http.core.NettyRequest.entryAt(core.clj:146)
  ;;       at clojure.lang.RT.find(RT.java:807)
  ;;       at clojure.core$find.invoke(core.clj:1465)
  ;;       at schema.core$map_walker$fn__10092$fn__10095.invoke(core.clj:741)
  ;;       at schema.core$map_walker$fn__10092.invoke(core.clj:728)
  ;;       at schema.core$eval10188$fn__10189$fn__10206.invoke(core.clj:840)
  ;;       at clojure.core$comp$fn__4492.invoke(core.clj:2436)
  ;;       at bolt.user$eval46172$render_login_form__46175.invoke(user.clj:81)
  ;;       at bolt.user.login.Login$fn__47684.invoke(login.clj:69)
  ;;       at bolt.util$wrap_schema_validation$fn__45964.invoke(util.clj:66)
  ;;       at bidi.ring$eval11505$fn__11506.invoke(ring.clj:22)
  ;;       at bidi.ring$eval11484$fn__11485$G__11475__11494.invoke(ring.clj:13)
  ;;       at bidi.ring$make_handler$fn__11510.invoke(ring.clj:35)
  ;;       at clojure.core$some_fn$sp2__6921.invoke(core.clj:7144)
  ;;       at aleph.http.server$wrap_stream__GT_input_stream$fn__31772.invoke(server.clj:392)
  ;;       at aleph.http.server$handle_request$fn__31696$f__12499__auto____31697.invoke(server.clj:153)
  ;;       at clojure.lang.AFn.run(AFn.java:22)
  ;;       at io.aleph.dirigiste.Executor$Worker$1.run(Executor.java:49)
;;       at java.lang.Thread.run(Thread.java:745)
;;
;; NettyRequest is defined with potemkin/def-derived-map. Perhaps this is not compatible with how schema validates.
;; Workaround is to remove request validation.

(s/defn render-login-form :- s/Str
  [component :- (s/protocol p/LoginFormRenderer)
   req ;; :- Request
   model :- {:form Form
             (s/optional-key :login-failed?) s/Bool
             (s/optional-key :post-login-redirect) s/Str}]
  (p/render-login-form component req model))

;; User form renderer API

(s/defn render-signup-form :- s/Str
  [component :- (s/protocol p/UserFormRenderer)
   req ;; :- Request
   model :- {:form Form}]
  (p/render-signup-form component req model))

(s/defn render-welcome :- s/Str
  [component :- (s/protocol p/UserFormRenderer)
   req ;; :- Request
   model :- {}]
  (p/render-welcome component req model))

(s/defn render-welcome-email-message :- EmailMessage
  [component :- (s/protocol p/UserFormRenderer)
   model :- {:email-verification-link s/Str}]
  (p/render-welcome-email-message component model))

(s/defn render-email-verified :- s/Str
  [component :- (s/protocol p/UserFormRenderer)
   req ;; :- Request
   model :- {:email EmailAddress
             s/Keyword s/Any}]
  (p/render-email-verified component req model))

(s/defn render-reset-password-request-form :- s/Str
  [component :- (s/protocol p/UserFormRenderer)
   req ;; :- Request
   model :- {:form Form
             (s/optional-key :email-failed?) s/Bool}]
  (p/render-reset-password-request-form component req model))

(s/defn render-reset-password-email-message :- EmailMessage
  [component :- (s/protocol p/UserFormRenderer)
   model :- {:link s/Str}]
  (p/render-reset-password-email-message component model))

(s/defn render-reset-password-link-sent-response :- s/Str
  [component :- (s/protocol p/UserFormRenderer)
   req ;; :- Request
   model :- {:email EmailAddress}]
  (p/render-reset-password-link-sent-response component req model))

(s/defn render-password-reset-form :- s/Str
  [component :- (s/protocol p/UserFormRenderer)
   req ;; :- Request
   model :- {:form Form
             s/Keyword s/Any}]
  (p/render-password-reset-form component req model))

(s/defn render-password-changed-response :- s/Str
  [component :- (s/protocol p/UserFormRenderer)
   req ;; :- Request
   model :- {}]
  (p/render-password-changed-response component req model))

;; Error form renderer API

(s/defn render-error :- s/Str
  [component :- (s/protocol p/ErrorRenderer)
   req ;; :- Request
   model :- {:error-type (s/enum :user-already-exists)
             s/Keyword s/Any}]
  (p/render-error-response component req model))

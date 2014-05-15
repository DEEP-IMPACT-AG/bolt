;; Copyright © 2014, JUXT LTD. All Rights Reserved.

(ns cylon.authorization
  (:require
   [schema.core :as s]
   [cylon.restricted :as restricted]))

;; Now we can define authorization for Ring handlers (and other
;; functions). In Cylon, it is handlers that are restricted, rather than
;; routes. Handlers are 'closer' to the data, since they serve it, and
;; it is better to consider authorization at the level of the data,
;; rather than define authorizations in the routing logic. The design
;; goal is for Cylon to be completely agnostic to the means by which
;; handlers are accessed, whether via a particular routing library
;; (Compojure, bidi, etc.) or via some other mechanism.

;; An Authorizer is responsible for protected sensitive resources in
;; addition to determining the access credentials of a potential
;; accessor.

(defprotocol Authorizer
  ;; Determine if given credentials (found in request) meet a given
  ;; requirement
  (authorized? [_ req requirement]))

(defrecord RestrictedHandler [f authorizer requirement rejectfn]
  restricted/Restricted
  (restricted/authorized? [this req]
    (authorized? authorizer req requirement))

  clojure.lang.IFn
  (invoke [this req]
    (if (restricted/authorized? this req)
      (f req)
      ;; If you don't want this default, call authorized? first
      (rejectfn req))))

(defn restrict-fn
  "Restrict a given function, such as a Ring handler, by some classification (which could be a set of roles, or anything that indicates the qualifying credentials a caller must exhibit in order to call the function. A function (or IFn) is returned."
  [f authorizer requirement rejectfn]
  (->RestrictedHandler f authorizer requirement rejectfn))

(defn restrict-handler
  ([handler authorizer requirement rejectfn]
     (restrict-fn handler authorizer requirement rejectfn))
  ([handler authorizer requirement]
     (restrict-fn handler authorizer requirement
                  (constantly {:status 401 :body "Unauthorized"}))))

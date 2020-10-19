(ns coral.core
  "A generic (in-process) impl for correlating messages (e.g. request/response) in distributed systems.
   Started out as a Kafka helper, but quickly realised there is nothing Kafka-specific here."
  (:require [clojure.core.cache.wrapped :as cw]
            [coral.protocol :as proto])
  (:import (clojure.lang ExceptionInfo)))

;; will work with any type of caching strategy,
;; but TTL is much preferable
(deftype Correlator [cache-atom]
  proto/ICorrelator

  (register-id! [_ cid exists!]
    (let [exists? (volatile! true)
          p (->> (fn [_]
                   (vreset! exists? false)
                   (promise))
                 (cw/lookup-or-miss cache-atom cid))]
      (if @exists?
        (exists! cid)
        p)))

  (deliver-id! [_ cid v not-found!]
    (try                ;; throw in order to prevent caching of v
      (when-let [p (->> #(throw (ex-info "Preventing a cache-miss" {::not-found %}))
                        (cw/lookup-or-miss cache-atom cid))]
        (deliver p v)
        (cw/evict cache-atom cid)
        true)
      (catch ExceptionInfo exi
        (or
          ;; our own exception (handle it)
          (some-> (ex-data exi) ::not-found not-found!)
          ;; some other exception (let it bubble up)
          (throw exi)))))

  (clear! [_]
    (swap! cache-atom empty))
  )

(defn- throw-exists! [id]
  (throw
    (IllegalStateException.
      (format "Correlation ID [%s] is already in-flight!" id))))

(defn- throw-not-found! [id]
  (throw
    (IllegalStateException.
      (format "Correlation ID [%s] is not in-flight!" id))))

;; PUBLIC API
;; ==========

(defn ^Correlator ttl-id-correlator
  "Constructor function for a `Correlator` using a TTL cache
   under the covers. This means that keys have to be 'correlated'
   within a period, but ensures that even if they're not, they
   are still going to be (eventually) evicted, thus eliminating
   the possibility of memory leaks."
  ([]
   (ttl-id-correlator 10000))
  ([ttl]
   (ttl-id-correlator {} ttl))
  ([pending ttl]
   (-> pending
       (cw/ttl-cache-factory :ttl ttl)
       Correlator.)))

(defn id->promise
  "Given a correlator (`Correlator` instance) and an <id>,
   adds it to the cache, and returns a promise (to be delivered
   - see `deliver-for-id`). In case of an insertion conflict,
   returns whatever <exists!> returns (defaults to <throw-exists!>)."
  ([correlator id]
   (id->promise correlator id throw-exists!))
  ([^Correlator correlator id exists!]
   (proto/register-id! correlator id exists!)))

(defn deliver-for-id
  "Given a correlator (`Correlator` instance) and an <id>,
   delivers <v> to the promise associated with it, evicts it
   from the cache, and returns `true`. In case <id> can't be found,
   returns whatever <not-found!> returns (defaults to <throw-not-found!>)."
  ([correlator id v]
   (deliver-for-id correlator id v throw-not-found!))
  ([^Correlator correlator id v not-found!]
   (proto/deliver-id! correlator id v not-found!)))

(defn clear-all!
  "Empties this correlator's internal cache."
  [^Correlator correlator]
  (proto/clear! correlator))


(ns ictops.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [ictops.store :as store]
            [ictops.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-system! st {:system-id "SYS-1" :client-id "client-1"
                                :name "payments-cluster"
                                :sla-response-minutes 30
                                :required-certifications #{"linux-admin" "network-tier2"}})
    st))

(defn- respond [minutes certs]
  {:op :approve-incident-response :effect :propose :system-id "SYS-1"
   :response-time-minutes minutes :technician-certifications certs
   :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-within-sla-and-fully-certified
  (let [st (fresh-store)
        v (governor/check req {} (respond 20 #{"linux-admin" "network-tier2"}) st)]
    (is (:ok? v))))

(deftest ok-at-exact-sla-and-with-extra-certs
  (testing "response time exactly at SLA and a superset of certs is within margin"
    (let [st (fresh-store)
          v (governor/check req {} (respond 30 #{"linux-admin" "network-tier2" "security-tier1"}) st)]
      (is (:ok? v)))))

(deftest hard-on-sla-exceeded
  (testing "SLA is arithmetic, not a best effort"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (respond 45 #{"linux-admin" "network-tier2"})
                                          :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :sla-exceeded (:rule %)) (:violations v))))))

(deftest hard-on-certification-coverage-incomplete
  (testing "a partial-coverage response is not permitted"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (respond 20 #{"linux-admin"}) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :certification-coverage-incomplete (:rule %)) (:violations v))))))

(deftest hard-on-unknown-system
  (let [st (fresh-store)
        v (governor/check req {} (assoc (respond 20 #{"linux-admin" "network-tier2"})
                                        :system-id "SYS-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-system (:rule %)) (:violations v)))))

(deftest hard-on-foreign-system
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (respond 20 #{"linux-admin" "network-tier2"}) st)]
      (is (:hard? v))
      (is (some #(= :system-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (respond 20 #{"linux-admin" "network-tier2"}) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (respond 20 #{"linux-admin" "network-tier2"})
                                        :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest escalates-emergency-override
  (let [st (fresh-store)
        v (governor/check req {} {:op :approve-emergency-override :effect :propose
                                  :system-id "SYS-1" :confidence 0.9 :stake :high} st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (respond 20 #{"linux-admin" "network-tier2"}) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

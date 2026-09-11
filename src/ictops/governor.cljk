(ns ictops.governor
  "ICTOperationsTechniciansGovernor — the independent safety/
  traceability layer for the ISCO-08 3511 community ICT operations
  technicians actor (itonami actor pattern, ADR-2607011000 /
  CLAUDE.md Actors section). Modeled on cloud-itonami-isco-4311's
  bookkeeping.governor. Ops twist: an incident response time is
  arithmetic comparison against the registered SLA ceiling, and the
  responding technician's certifications must fully cover the
  system's registered required-certifications set — an SLA is a
  number, not a best effort, and a partial-coverage response on a
  system beyond the technician's registered qualification is not
  permitted.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance — the organization must be registered.
    2. no-actuation      — proposal :effect must be :propose.
    3. system basis        — an incident-response approval must cite
                           a REGISTERED system belonging to this
                           client.
    4. SLA arithmetic       — the proposed response-time-minutes must
                           not exceed the system's registered
                           :sla-response-minutes (arithmetic, not a
                           best effort).
    5. certification coverage — the proposed technician-certifications
                           set must be a superset of the system's
                           registered :required-certifications set (no
                           partial-coverage response).
  ESCALATION invariants (:escalate? true, human sign-off):
    6. :op :approve-emergency-override (bypassing normal change
                           control under incident pressure).
    7. low confidence (< `confidence-floor`)."
  (:require [clojure.set :as set]
            [ictops.store :as store]))

(def confidence-floor 0.6)

(defn- hard-violations [{:keys [request proposal]} client-record sys]
  (let [{:keys [op response-time-minutes technician-certifications]} proposal
        approve? (= :approve-incident-response op)]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (and approve? (nil? sys))
      (conj {:rule :unknown-system :detail "未登録 system へのインシデント対応承認は不可"})

      (and approve? sys (not= (:client-id sys) (:client-id request)))
      (conj {:rule :system-wrong-client :detail "system が別 client のもの"})

      (and approve? sys (number? response-time-minutes)
           (> response-time-minutes (:sla-response-minutes sys)))
      (conj {:rule :sla-exceeded
             :detail (str "対応時間 " response-time-minutes "分 > 登録済み SLA "
                          (:sla-response-minutes sys) "分（SLA は算術であってベストエフォートではない）")})

      (and approve? sys
           (not (set/superset? (set technician-certifications) (:required-certifications sys))))
      (conj {:rule :certification-coverage-incomplete
             :detail (str "資格未充足 "
                          (vec (set/difference (:required-certifications sys) (set technician-certifications)))
                          "（この system への対応は登録済み要求資格を完全に充足する技術者のみ許可）")}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `ictops.store/Store`. Pure — never mutates the
  store."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        sys (some->> (:system-id proposal) (store/system store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record sys)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        risky-op? (= :approve-emergency-override (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))

(ns ictops.store
  "SSoT for the ISCO-08 3511 community ICT operations technicians
  actor (itonami actor pattern, ADR-2607011000 / CLAUDE.md Actors
  section). Modeled on cloud-itonami-isco-4311's bookkeeping.store.

  Domain:

    client — a registered organization (:client-id, :name)
    system — a registered monitored system {:system-id :client-id
             :name :sla-response-minutes number
             :required-certifications #{cert-str}}.
             `:sla-response-minutes` is the registered maximum
             response time a proposed incident response must not
             exceed; `:required-certifications` is the registered set
             a responding technician's certifications must fully
             cover (no partial-coverage response on a system beyond
             the technician's registered qualification).
    record — a committed operating record (approved incident
             response) — written ONLY via commit-record!.
    ledger — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (system [s system-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-system! [s sys])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (system [_ system-id] (get-in @a [:systems system-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-system! [s sys]
    (swap! a assoc-in [:systems (:system-id sys)] sys) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :systems {} :records [] :ledger []}
                                   seed)))))

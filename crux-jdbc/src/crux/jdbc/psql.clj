(ns ^:no-doc crux.jdbc.psql
  (:require [crux.jdbc :as j]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as jdbcr]
            [clojure.tools.logging :as log]
            [crux.system :as sys]))

(defn- check-tx-time-col [pool]
  (when-not (= "timestamp with time zone"
               (-> (jdbc/execute-one! pool
                                      ["SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'tx_events' AND COLUMN_NAME = 'tx_time'"]
                                      {:builder-fn jdbcr/as-unqualified-lower-maps})
                   :data_type))
    (log/warn (str "`tx_time` column not in UTC format. "
                   "See https://github.com/juxt/crux/releases/tag/20.09-1.12.1 for more details."))))

(defn ->dialect {::sys/args {:drop-table? {:spec ::sys/boolean, :default false}}}
  [{:keys [drop-table?]}]
  (reify j/Dialect
    (db-type [_] :postgresql)

    (setup-schema! [_ pool]
      (when drop-table?
        (jdbc/execute! pool ["DROP TABLE IF EXISTS tx_events"]))

      (doto pool
        (jdbc/execute! ["
CREATE TABLE IF NOT EXISTS tx_events (
  event_offset SERIAL PRIMARY KEY,
  event_key VARCHAR,
  tx_time TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  topic VARCHAR NOT NULL,
  v BYTEA NOT NULL,
  compacted INTEGER NOT NULL)"])

        (jdbc/execute! ["CREATE INDEX IF NOT EXISTS tx_events_event_key_idx ON tx_events(event_key)"])
        (check-tx-time-col)))))

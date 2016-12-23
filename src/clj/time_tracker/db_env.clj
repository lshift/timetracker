(ns time-tracker.db-env
  (:require [environ.core :refer [env]]))

(defn db-from-env []
  (let [db-url (env :database-url)]
    (if (nil? db-url)
      (throw (Exception. "DATABASE_URL isn't set!"))
      (let [db-uri (java.net.URI. db-url)
            [username password] (clojure.string/split (.getUserInfo db-uri) #":")
            port (.getPort db-uri)
            port (if (= port -1) 5432 port)] ; provide the default Postgres port
        {:subprotocol "postgresql"
         :jdbc-url (format "jdbc:postgresql://%s:%s%s" (.getHost db-uri) port (.getPath db-uri))
         :subname (format "//%s:%s%s" (.getHost db-uri) port (.getPath db-uri))
         :user username
         :password password}))))

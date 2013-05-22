(ns newscat.core
  (:require [clojure.string :as string]
            [clojure.java.jdbc :as j]
            [clojure.java.jdbc.sql :as s]
            [clj-http.client :as client]
            [clojure.data.json :as json])
  (:use [boilerpipe-clj.core]))


(defn get-article-text
  "Given a URL, extracts the article text and returns it as a string"
  [url]
  (get-text (slurp url)))

(def query-template
  "http://www.reddit.com/r/subreddit/new.json?sort=top&limit=50")

(def categories
  ["politics" "business" "technology"
   "sports" "science" "entertainment"])

(def db
  {:classname   "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname     "database.db"
   })

(defn create-cat-table [category]
  (try (j/with-connection db
         (j/create-table category
                         [:url :text]
                         [:content :text]))
       (catch Exception e (println e))))

(defn make-tables
  "creates all the category tables in the database defined as 'db'"
  []
  (map #(create-cat-table %) categories))

(defn run-query
  "Given a category, run a query and return results"
  [category query-template]
  (try (:body
        (client/get
         (string/replace query-template
                         #"subreddit"
                         category)))
       (catch Exception e (println e))))


(defn extract-text
  "given the result of a query, extract the article text and return it
   as a list of maps of the form {:url url, :content article-text}"
  [query-result]
  (map #(hash-map :url (->> % :data :url)
                  :content (get-article-text (->> % :data :url)))
       (->> (json/read-str query-result :key-fn keyword)
            :data
            :children)))


(defn store-query
  "given a category and a query template, stores result of query in table"
  [category query-template]
  (let [res (extract-text (run-query category query-template))]
    (map #(j/insert! db category %)
         res)))

(defn query-and-store-all
  "runs queries and stores results for all categories"
  [categories query-template]
  (map #(store-query % query-template)
       categories))

(defn foo
  "I don't do a whole lot."
  [x]
  (println x "Hello, World!"))

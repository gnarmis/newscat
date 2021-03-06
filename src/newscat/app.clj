(ns newscat.app
  "Sets up the web app's routes and handlers"
  (:require [clojure.data.json :as json]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [noir.util.middleware :as nm]
            [newscat.train :as train]
            [clj-http.client :as client])
  (:use [compojure.core]
        [environ.core])
  (:import [org.jsoup Jsoup]))

(defn wrap-response
  "Helper for wrapping responses for Ring"
  [content status] {:status status
                    :headers {"Content-Type" "application/json"}
                    :body content})



(defn readability-parse
  "Given a url, queries Readability Parser API and parses JSON response
  into hash-map"
  [url]
  (let [endpoint "http://www.readability.com/api/content/v1/parser?"
        token    (env :readability-parser-api-token)
        query    (str endpoint "url=" url "&token=" token)
        res      (client/get query)]
    (try (json/read-str (->> res :body)
                        :key-fn keyword)
         (catch Exception e {:error e}))))

(defn content-extract
  "Given a url-details hash-map (returned by readability-parse), parses
   and returns the article content as a plain text string"
  [url-details]  
  (if (contains? url-details :error)
    ""
    (->> url-details :content Jsoup/parse .text)))


(def categorizer
  "The actual categorizer"
  (train/newscat))

(defn categorize
  "Request handler"
  [params]
  (try
    (wrap-response
     (json/write-str
      (let [url-details
            (readability-parse (->> params vec flatten second))]
        (merge url-details
               (categorizer (content-extract url-details)))))
     202)
    (catch Exception e
      (wrap-response e
                     409))))


(def app-routes
  "Vector of forms that define how routes in the app behave"
  [(GET "/categorize.json" {params :params} (categorize params))
   (route/not-found "Categorizer")])

(def app
  "Handler for the application, for Ring."
  (nm/app-handler app-routes))


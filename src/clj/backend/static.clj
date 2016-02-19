(ns backend.static
  (:require
            [hiccup.core :as hiccup]
            [hiccup.page :as page]
            [metosin.ring.util.cache :as cache]
            [metosin.ring.util.hash :as hash]
            [ring.middleware.content-type :as content-type]
            [ring.middleware.webjars :as webjars]
            [ring.util.http-response :as resp]
            [ring.util.response :as response]))

(defn index-page [config]
  (hiccup/html
    (page/html5
      [:head
       [:title "Building Permits"]
       [:meta {:charset "utf-8"}]
       [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge"}]
       [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
       [:link {:rel "shortcut icon" :href (str "favicon.ico?v=" (hash/memo-resource-hash "public/favicon.ico"))}]
       (page/include-css (str "css/main.css?v=" (hash/memo-resource-hash "css/main.css")))]
      [:body
       [:div.app-wrapper {:id "app"}]
       [:div#dev]
       (page/include-js (str "js/main.js?v=" (hash/memo-resource-hash "js/main.js")))])))

(defn create-handler [config]
  (let [index (index-page config)]
    (-> (fn [{:keys [request-method uri] :as req}]
          (if (= request-method :get)
            (condp re-matches uri
              #"\/"            (-> (resp/ok index)
                                   (resp/content-type "text/html")
                                   (cache/cache-control cache/no-cache))
              #"\/js\/.*"      (response/resource-response uri)
              #"\/css\/.*"     (response/resource-response uri)
              (response/resource-response (str "public" uri)))))
        (webjars/wrap-webjars)
        (content-type/wrap-content-type)
        (cache/wrap-cache {:value    cache/cache-30d
                           :default? true}))))

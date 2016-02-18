(set-env!
  ; Test path can be included here as source-files are not included in JAR
  ; Just be careful to not AOT them
  :source-paths #{"src/cljs" "src/less" "test/clj"}
  :resource-paths #{"src/clj" "src/cljc" "resources"}
  :dependencies '[[org.clojure/clojure    "1.8.0"]
                  [org.clojure/clojurescript "1.7.228"]
                  [org.clojure/core.async "0.2.374"]
                  [org.clojure/tools.logging "0.3.1"]

                  [boot/core              "2.5.5"      :scope "test"]
                  [adzerk/boot-cljs       "1.7.228-1"  :scope "test"]
                  [adzerk/boot-cljs-repl  "0.3.0"      :scope "test"]
                  [com.cemerick/piggieback "0.2.1"     :scope "test"]
                  [weasel                 "0.7.0"      :scope "test"]
                  [org.clojure/tools.nrepl "0.2.12"    :scope "test"]
                  [adzerk/boot-reload     "0.4.2"      :scope "test"]
                  [deraen/boot-less       "0.5.0"      :scope "test"]
                  ;; For boot-less
                  [org.slf4j/slf4j-nop    "1.7.13"     :scope "test"]
                  [deraen/boot-ctn        "0.1.0"      :scope "test"]

                  ; Backend
                  [http-kit "2.1.19"]
                  [reloaded.repl "0.2.1"]
                  [com.stuartsierra/component "0.3.1"]
                  [metosin/palikka "0.3.0"]
                  [metosin/kekkonen "0.2.0-SNAPSHOT"]
                  [metosin/reagent-dev-tools "0.1.0"]
                  [metosin/ring-http-response "0.6.5"]
                  [metosin/metosin-common "0.1.3"]
                  [hiccup "1.0.5"]
                  [ring-webjars "0.1.1"]

                  ; Frontend
                  [reagent "0.6.0-alpha"]
                  [cljs-http "0.1.38"]
                  [binaryage/devtools "0.5.2"]

                  ; LESS
                  [org.webjars/bootstrap "3.3.6"]])

(require
  '[adzerk.boot-cljs      :refer [cljs]]
  '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl repl-env]]
  '[adzerk.boot-reload    :refer [reload]]
  '[deraen.boot-less      :refer [less]]
  '[deraen.boot-ctn       :refer [init-ctn!]]
  '[backend.main]
  '[reloaded.repl         :refer [go reset start stop system]])

; Watch boot temp dirs
(init-ctn!)

(deftask start-app
  [p port   PORT int  "Port"]
  (let [x (atom nil)]
    (with-post-wrap fileset
      (swap! x (fn [x]
                 (if x
                   x
                   (do (backend.main/setup-app! {:port port})
                       (go)))))
      fileset)))

(task-options!
  pom {:project 'metosin/kekkonen-building-permit-example
       :version "0.1.0-SNAPSHOT"
       :description "a complex simulated real-life case example showcase project"
       :license {"The MIT License (MIT)" "http://opensource.org/licenses/mit-license.php"}}
  aot {:namespace #{'backend.main
                    'com.stuartsierra.component
                    'com.stuartsierra.dependency
                    'clojure.tools.logging.impl}}
  jar {:main 'backend.main}
  cljs {:source-map true}
  less {:source-map true})

(deftask dev
  [p port       PORT int  "Port for web server"]
  (comp
    (watch)
    (less)
    (reload)
    (cljs-repl)
    (cljs)
    (start-app :port port)))

(deftask build []
  (comp
    (less :compression true)
    (cljs :optimizations :advanced)
    (pom)
    (uber)
    (aot)
    (jar :file "app.jar")
    (sift :include #{#"app.jar"})
    (target :dir #{"target"})))

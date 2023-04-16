(defproject aerial.saite "1.6.0"
  :description "Interactive document and visualization system - exploration, presentation, publication"
  :url "https://github.com/jsa-aerial/saite"
  :license {:name "MIT"
            :url "http://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [org.clojure/clojurescript "1.10.439"]
                 [org.ow2.asm/asm  "7.1"] ; tech.v3 req???
                 [org.clojure/core.async "1.4.627"
                  :exclusions [org.ow2.asm/asm-all]] ; tech.v3 req??
                 [org.clojure/data.csv "1.0.0"]

                 ;;; These are needed due to ensure correct versions
                 ;;; w/o actually requiring full lib stacks in uberjar
                 [techascent/tech.jna "3.23"]

                 [clj-commons/pomegranate "1.2.1"]    ; dynamic loader
                 [clj-commons/fs "1.5.2"]             ; zip/tar
                 [com.rpl/specter "1.1.3"]

                 [aerial.hanami "0.18.0"]
                 [cljsjs/codemirror "5.44.0-1"]
                 [cljsjs/highlight "9.12.0-2"]
                 [cljsjs/mathjax "2.7.5-0"]
                 [cljsjs/filesaverjs "1.3.3-0"]
                 [cljsjs/jszip "3.1.3-0"]

                 [org.nrepl/incomplete "0.1.0"]       ; Completion support

                 [net.apribase/clj-dns "0.1.0"]
                 [aerial.fs "1.1.6"]
                 [aerial.utils "1.2.0"]
                 [aerial.bio.utils "2.1.0"]]

  :plugins [[lein-figwheel "0.5.16"]
            [lein-cljsbuild "1.1.7" :exclusions [[org.clojure/clojure]]]]

  :source-paths ["src/cljc" "src/clj" "src/cljs"]

  :prep-tasks ["compile" ["cljsbuild" "once" "min"]]

  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src/cljs"]
                :jar true

                ;; The presence of a :figwheel configuration here
                ;; will cause figwheel to inject the figwheel client
                ;; into your build
                :figwheel {:on-jsload "aerial.saite.core/on-js-reload"
                           ;; :open-urls will pop open your application
                           ;; in the default browser once Figwheel has
                           ;; started and compiled your application.
                           ;; Comment this out once it no longer serves you.
                           :open-urls ["http://localhost:3450/index.html"]}

                :compiler {:main aerial.saite.core
                           :asset-path "js/compiled/out"
                           :output-to
                           "resources/public/js/compiled/saite.js"
                           :output-dir "resources/public/js/compiled/out"
                           :source-map-timestamp true
                           ;; To console.log CLJS data-structures make
                           ;; sure you enable devtools in Chrome
                           ;; https://github.com/binaryage/cljs-devtools
                           :preloads [devtools.preload]}}
               ;; This next build is a compressed minified build for
               ;; production. You can build this with:
               ;; lein cljsbuild once min
               {:id "min"
                :source-paths ["src/cljs"]
                :compiler {:output-to
                           "resources/public/js/compiled/saite.js"
                           :main aerial.saite.core
                           :optimizations :simple
                           :pretty-print false}}]}

  :figwheel {;; :http-server-root "public" ;; default and assumes "resources"
              :server-port 3450 ;; default
             ;; :server-ip "127.0.0.1"

             :repl-eval-timeout 30000

             :css-dirs ["resources/public/css"] ;; watch and update CSS

             ;; Start an nREPL server into the running figwheel process
             ;; :nrepl-port 7888

             ;; Server Ring Handler (optional)
             ;; if you want to embed a ring handler into the figwheel http-kit
             ;; server, this is for simple ring servers, if this

             ;; doesn't work for you just run your own server :) (see lein-ring)

             ;; :ring-handler hello_world.server/handler

             ;; To be able to open files in your editor from the heads
             ;; up display you will need to put a script on your path.
             ;; that script will have to take a file path and a line
             ;; number ie. in ~/bin/myfile-opener #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"

             ;; if you are using emacsclient you can just use
             ;; :open-file-command "emacsclient"

             ;; if you want to disable the REPL
             ;; :repl false

             ;; to configure a different figwheel logfile path
             ;; :server-logfile "tmp/logs/figwheel-logfile.log"

             ;; to pipe all the output to the repl
             ;; :server-logfile false
             }

  ;; Setting up nREPL for Figwheel and ClojureScript dev
  ;; Please see:
  ;; https://github.com/bhauman/
  ;;   (uri cont'd) lein-figwheel/wiki/Using-the-Figwheel-REPL-within-NRepl
  :profiles {:dev {:dependencies [[binaryage/devtools "0.9.9"]
                                  [figwheel-sidecar "0.5.16"]
                                  [cider/piggieback "0.3.3"]]
                   ;; need to add dev source path here to get user.clj loaded
                   :source-paths ["src" "dev"]
                   ;; for CIDER
                   :repl-options {:nrepl-middleware
                                  [cider.piggieback/wrap-cljs-repl]}
                   ;; need to add the compliled assets to the :clean-targets
                   :clean-targets ^{:protect false}
                   ["resources/public/js/compiled" :target-path]}})

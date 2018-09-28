(ns arachne.error.format
  (:refer-clojure :exclude [format])
  (:require [arachne.repl :refer [*color* cfstr cfprint c colorize]]
            [io.aviso.ansi :as ansi]
            [io.aviso.exception :as aviso]
            [expound.alpha :as expound]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [clojure.pprint :as pprint]
            [clojure.stacktrace :as st])
  (:import [java.io StringWriter]
           [clojure.lang IExceptionInfo]
           [java.lang StackTraceElement Throwable]))


(defn- source-location
  "Return a string with the class, file and line number of the given exception"
  [^Throwable e]
  (let [^StackTraceElement ste (first (.getStackTrace e))]
    (str (.getClassName ste) "(" (.getFileName ste) ":" (.getLineNumber ste) ")")))

(defn justify
  "Clean up newlines so everything is left-justified"
  [msg]
  (if (<= (count (str/split-lines msg)) 1)
    msg
    (let [lines (str/split-lines msg)
          baseline-lines (filter #(not (str/blank? %)) (drop 1 lines))
          baseline (apply min (map #(count (re-find #"^\s+" %)) baseline-lines))
          re (re-pattern (str "^\\s{" baseline "}"))]
      (str/join "\n"
        (map (fn [line]
               (str/replace line re ""))
          lines)))))

(defn- indent
  [n s]
  (let [lines (str/split-lines s)
        sep (apply str "\n" (repeat n " "))]
    (str/join sep lines)))

(def ^:dynamic *format-err* nil)

(defn- pprint-str
  "pprint to a string, but catch exceptions so it won't blow up the whole process"
  [data]
  (try
    (with-out-str
      (binding [pprint/*print-right-margin* 70]
        (pprint/pprint data)))
    (catch Throwable t
      (alter-var-root #'arachne.error.format/*format-err* (constantly t))
      (str "<<< Exception while pretty printing:" (.getName (.getClass t))
        ", bound to arachne.error.format/*format-err* for further inspection>>>"))))

(defn pprint-str-truncated
  "Return a string of the given data, truncated to the given number of lines.
  Includes indentation. Only suitable in the context of an exception message."
  [data n]
  (let [raw (pprint-str data)
        lines (str/split-lines raw)
        lines-to-print (if (< (count lines) n)
                         lines
                         (concat (take n lines)
                           [(str "... truncated " (- (count lines) n) " more lines, inspect ex-data for full value")]))]
    (->> lines-to-print
      (map (fn [l] (str "    " l)))
      (str/join "\n"))))

(defn format
  "Formats a String message for the exception, and returns a string. Options are:

  :color - use rudimentary ANSI color in the output (default true).
  :suggestions - Show any suggestions in the error (default true).
  :ex-data-summary - Show a table of the keys available in the exception's ex-data (default true.)
  :cause - Show the exception's cause (default true.)
  :stacktrace - Print a stacktrace for the exception (default false).
  :pretty-stacktrace - Print a stacktrace for the exception, formatted using io.aviso/pretty (default false).

  "
  [^Throwable
   e {:keys [color suggestions stacktrace ex-data-summary
             cause pretty-stacktrace]
      :as opts
      :or {color *color*
           suggestions true
           ex-data-summary true
           cause true
           stacktrace false
           pretty-stacktrace false}}]
  (binding [*color* color]
    (with-out-str
      (let [d (ex-data e)]

        (print "\n")

        (let [header (str "ERROR: " (.getName (class e)) " " (:arachne.error/type d))]
          (cfprint ansi/bold-red (apply str (repeat (count header) "=")) "\n")
          (cfprint ansi/bold-red header "\n")
          (cfprint ansi/bold-red (apply str (repeat (count header) "-"))))

        (c ansi/reset-font)

        (cfprint ansi/bold-red "\n\nMESSAGE:\n\n")
        (print (colorize (or (:arachne.error/message d)
                           (.getMessage e))))


        (when-let [ex (:arachne.error/explanation d)]
          (cfprint ansi/bold-red "\n\nEXPLANATION:\n\n")
          (print (colorize ex)))

        (when-let [spec (:arachne.error/spec d)]
          (cfprint ansi/bold-red "\n\nSPEC FAILURE:\n\n")
          (c ansi/reset-font)

          (when-let [ed (:arachne.error/explain-data d)]
            (c ansi/reset-font)
            (binding [s/*explain-out*
                      (expound/custom-printer {:theme (if color :figwheel-theme :none)})]
              (s/explain-out ed))
            (c ansi/reset-font)))

        (when (and (:arachne.error/suggestions d)
                (not (empty? (:arachne.error/suggestions (ex-data e)))))
          (cfprint ansi/bold-red "\n\nSUGGESTIONS:\n\n")
          (let [suggs (:arachne.error/suggestions (ex-data e))]
            (doseq [[i s] (map-indexed (fn [i s] [i s]) suggs)]
              (print
                " - "
                (indent 3 (colorize s)))
              (when-not (= (inc i) (count suggs))
                (print "\n")))))

        (when (and ex-data-summary (instance? IExceptionInfo e))
          (cfprint ansi/bold-red "\n\nEX-DATA:\n\n")

          (c ansi/reset-font)
          (println "The exception had a map of additional data fields:\n")
        (let [rows (->> (ex-data e)
                     (filter #(not= "arachne.error" (namespace (key %))))
                     (map (fn [[k v]]
                            {"Key" k
                             "Description" (get-in d [:arachne.error/ex-data-docs k] "(no description)")})))]
          (pprint/print-table rows))

          (print "\nYou may insepct this data by calling clojure.core/ex-data on the exception object.")

          (c ansi/reset-font))
        (when stacktrace
          (cfprint ansi/bold-red "\n\nFULL STACKTRACE:\n\n")
          (c ansi/reset-font)

          (st/print-cause-trace e)
          (c ansi/reset-font))

        (when pretty-stacktrace
          (cfprint ansi/bold-red "\n\nABRIDGED STACKTRACE:\n\n")
          (aviso/write-exception *out* e {:properties false})
          (c ansi/reset-font))

        (when (and cause (.getCause e))
          (cfprint ansi/bold-red "\n\nCAUSED BY\n↓↓↓↓↓↓↓↓↓\n")
          (println (format (.getCause e) opts)))

        ))) )

(defn truncate-str
  "Truncate a str after the given number of characters"
  [s chars]
  (if (< (count s) chars)
    s
    (str (subs s 0 chars) "...")))

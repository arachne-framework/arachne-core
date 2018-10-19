(ns arachne.core.descriptor
  "Handles building and managing Arachne's system descriptors. Provides
  a thin facade over Aristotle, to accomodate error reporting etc."
  (:refer-clojure :exclude [new update])
  (:require [arachne.core.util :as u]
            [arachne.core.descriptor.schema :as scm]
            [arachne.error :as e :refer [deferror error]]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [clojure.walk :as w]
            [clojure.java.io :as io]
            [arachne.log :as log]
            [arachne.aristotle :as ar]
            [arachne.aristotle.graph :as graph]
            [arachne.aristotle.registry :as reg]
            [arachne.aristotle.query :as q]
            [arachne.aristotle.validation :as v])
  (:import [java.util Date]
           [org.apache.jena.graph Graph]
           [org.apache.jena.shared LockMutex Lock]
           [org.apache.jena.sparql.algebra Op]
           [java.lang StackTraceElement]))

(reg/prefix :org.arachne-framework "http://arachne-framework.org/name/")
(reg/prefix :arachne.* "urn:arachne:")
(reg/prefix :clojure.* "urn:arachne:clojure:")

(def
  ^{:dynamic true
    :doc "Data regarding the provenance of descriptor updates ocurring in this stack context"}
  *provenance-data*
  nil)

(defn stack-provenance-txdata
  "Build provenance data based on the current stack frame, using the
  provided function symbol.

  If a stack-filter-pred is provided, the system will use the top
  stack frame that passes the predicate, otherwise current stack frame
  is used."
  ([function]
   (stack-provenance-txdata function (constantly false)))
  ([function stack-filter-pred]
   (let [stack (seq (.getStackTrace (Thread/currentThread)))
         ste ^StackTraceElement (first (filter stack-filter-pred stack))
         pdata {:arachne.provenance/function (symbol function)}]
     (if ste
       (assoc pdata :arachne.provenance/stack-frame
              {:arachne.stack-frame/class (.getClassName ste)
               :arachne.stack-frame/source-file (.getFileName ste)
               :arachne.stack-frame/source-line (.getLineNumber ste)})
       pdata))))

(defmacro with-provenance
  "Add provenance data based upon the current stack location to the
  binding of the *provenance-data* var inside the body."
  [& opts-and-body]
  (let [args (s/conform (:args (s/get-spec `with-provenance)) opts-and-body)
        function (:function args)
        stack-filter (-> args :options :stack-filter-pred)
        provenance (-> args :options :provenance)
        body (:body args)]
    `(let [pdata# ~(if stack-filter
                      `(stack-provenance-txdata ~function ~stack-filter)
                      `(stack-provenance-txdata ~function))]
       (binding [*provenance-data* (merge (if *provenance-data*
                                            (assoc pdata# :arachne.provenance/parent
                                                   *provenance-data*)
                                            pdata#) ~provenance)]
         ~@body))))

(s/def ::provenance map?)

(s/fdef with-provenance
  :args (s/cat :function any?
               :options (s/keys* :opt-un [::stack-filter-pred
                                          ::provenance])
               :body (s/* any?))
  :ret any?)

(defprotocol Synced
  "Thread safe synchronized box for an object"
  (update- [this f] "Update the object descriptor using the
  specified function, which is executed within a write lock.")
  (read- [this f] "Apply the given function within a read lock and
  return its result. The result should be a realized immutable value."))

(deftype Descriptor [^:volatile-mutable ^:public ^Graph graph ^Lock mutex]
  Object
  (toString [this]
    (str "#arachne.descriptor[hash=" (.hashCode this)
                 ", size=" (.size graph) "]"))
  Synced
  (update- [this f]
    (try
      (.enterCriticalSection mutex false)
      (set! graph (f graph))
      this
      (finally
        (.leaveCriticalSection mutex))))
  (read- [this f]
    (try
      (.enterCriticalSection mutex true)
      (f graph)
      (finally
        (.leaveCriticalSection mutex)))))

(def descriptor? #(satisfies? Synced %))

(s/def ::descriptor descriptor?)

(declare update!)
(declare add-file!)

(defn new
  "Create a new, empty descriptor."
  []
  (-> (Descriptor. (ar/graph :jena-mini) (LockMutex.))
    (add-file! "arachne/core/schema.rdf.edn")))

(s/fdef new
  :args (s/cat)
  :ret ::descriptor)

(def ^:private next-idx-q
  (q/build '[:group [] [?max (max ?idx)]
             [:bgp [_ :arachne.descriptor.tx/index ?idx]]]))

(defn- next-idx
  "Given a graph, return the next transaction index"
  [g]
  (let [result (q/run g '[?max] next-idx-q)]
    (if-let [max (ffirst result)]
      (inc max)
      0)))

(defn- update*
  [descriptor data metadata]
  (let [tx (graph/node '_)
        pdata (w/prewalk-replace {'_tx tx} metadata)
        delta (-> (ar/graph :simple)
                  (ar/add data)
                  (graph/reify :arachne.descriptor/tx tx)
                  (ar/add pdata))]
    (update- descriptor
      (fn [g]
        (-> g
            (ar/add delta)
            (ar/add {:rdf/about tx
                     :arachne.descriptor.tx/index (next-idx g)}))))))

(defn update!
  "Update a descriptor with the given data. Reifies the triples in the
  transaction and assocates them with a given transaction, including
  the given metadata (if supplied). Metadata may contain a blank
  variable named _tx, which will be replaced with the transaction
  resource.

  Adds additional provenance data from that currently bound to
  *provenance-data*."
  ([descriptor data]
   (update! descriptor data []))
  ([descriptor data metadata]
   (e/assert-args `update! descriptor data metadata)
   (let [metadata (concat metadata
                          (when *provenance-data*
                            [{:rdf/about '_tx
                              :arachne.descriptor.tx/provenance *provenance-data*}]))]
     (e/attempt ::update-exception {:data data
                                    :metadata metadata
                                    :descriptor descriptor}
       (update* descriptor data metadata)))))

(deferror ::descriptor-file-exception
  :message "Error adding file `:path` to descriptor."
  :explanation "While attempting to read a file to add it to the descriptor, an error was thrown."
  :suggestions ["See this exception's cause for details of what went wrong."
                "Make sure the file exists, and does not contain syntax errors"]
  :ex-data-docs {:path "The path of the file containing the error."})

(defn add-file!
  "Update a descriptor to contain the contents of the given
  classpath-relative RDF filename. All formats supported by Jena in
  addition to including RDF/EDN are supported."
  [descriptor path]
  (let [g (e/attempt ::descriptor-file-exception {:path path}
            (-> (ar/graph :simple) (ar/read path)))]
    (with-provenance `add-file!
      :provenance {:arachne.provenance/rdf-file (str path)}
      (update! descriptor g))))

(s/fdef update!
  :args (s/cat :descriptor ::descriptor
               :data ::graph/triples
               :metadata (s/? ::graph/triples))
  :ret ::descriptor)

(defn query
  "Query a descriptor. Arguments are as for arachne.aristotle.query/run,
  but passing in a descriptor as the first argument instead of a
  graph. Results are never lazy."
  [descriptor & args]
  (apply e/assert-args `query descriptor args)
  (read- descriptor
         (fn [g]
           (let [r (apply q/run g args)]
             (if (set? r) r (doall r))))))

(s/fdef query
   :args (s/cat :descriptor ::descriptor
                :bindings (s/? (s/coll-of ::graph/variable))
                :query (s/or :op #(instance? Op %)
                             :query :arachne.aristotle.query.spec/operation)
                :data (s/? :arachne.aristotle.query.spec/bindings)))

(defn pull
  "Pull an entity from a descriptor. Arguments are as for
  arachne.aristotle.query/pull, but passing a descriptor as the first
  argument instead of a graph."
  [descriptor subject pattern]
  (e/assert-args `pull descriptor subject pattern)
  (read- descriptor
         (fn [g] (q/pull g subject pattern))))

(s/fdef pull
   :args (s/cat :descriptor ::descriptor
                :subject (s/or :iri ::graph/iri
                               :blank ::graph/blank)
                :pattern ::q/pull-pattern))

(deferror ::update-exception
  :message "An error ocurred while updating the descriptor."
  :explanation "While attempting to add some new data to the descriptor, something went wrong. Most likely, there was somethign wrong with the RDF data supplied to Aristotle."
  :suggestions ["Look at this exception's \"cause\" to get some information on specifically what kind of error it was."
                "Inspect the offending data (found in the data attached to this exception) for anything that looks wrong."]
  :ex-data-docs {:data "The data provided in the call to `update`."
                 :metadata "The metadata also being added."
                 :descriptor "The descriptor at the time of the exception."})


(deferror ::validation-errors
  :message "Found :count errors while validating the configuration. See ex-data for individual exceptions."
  :explanation "After it was constructed, the descriptor was found to be invalid. There were :count different errors:\n :messages"
  :suggestions ["Check the errors key in the ex-data to retrieve the individual validation exceptions. You can then inspect them individually using `arachne.error/explain`."]
  :ex-data-docs {:errors "The collection of validation exceptions"
                 :count "The number of validation exceptions"
                 :messages "A string representation of all the error messages."
                 :descriptor "The invalid descriptor."})

(deferror ::consistency-error
  :message ":message"
  :explanation "The descriptor's OWL reasoner discovered an internal inconsistency in the RDF model. The type of the inconsistency was `:type`. "
  :suggestions ["Make sure that the data in the descriptor is valid and does not violate any OWL schema elements."
                "Pay attention to the error message for clues to what the specific problem is."]
  :ex-data-docs {:descriptor "The invalid descriptor."
                 :type "The type of the error"
                 :message "The error message"
                 :details "Detailed error map returned by the validator."})

(defn- default-validator
  "Return a seq of validation errors, one for each consistency error returned by Aristotle"
  [d]
  (read- d (fn [g]
             (->> (v/validate g [v/min-cardinality])
                  (filter ::v/error?)
                  (map (fn [e]
                         (e/arachne-ex ::consistency-error
                           {:descriptor d
                            :type (::v/type e)
                            :message (::v/description e)
                            :details (::v/details e)})))))))

(defn validate
  "Validate the given descriptor. If it contains any validation errors, throw an exception with the errors in its ex-data."
  [d]
  (let [fns (query d '[?fn]
                     '[:bgp [_ :arachne.descriptor.validator/fn ?fn]])
        fns (map first fns)
        fns (conj fns default-validator)
        errors (mapcat #(% d) fns)]
    (case (count errors)
      0 d
      1 (throw (first errors))
      (e/error ::validation-errors {:count (count errors)
                                    :errors errors
                                    :messages (apply str "\n" (str/join "\n\n" (map #(.getMessage ^Throwable %) errors)))
                                    :descriptor d})))
  d)

<?prefix :clojure=urn:arachne:clojure: ?>

<?about :clojure/Var ?>

Class for RDF entities that have a 1:1 correspondence with a Clojure
Var. The IRI of the entity should be of the form
`urn:clojure:<namespace>/<name>`, which Aristotle will automatically
convert back and forth to the corresponding Clojure symbol when
reading or writing RDF/EDN.

Instances of this class are the primary mechanism by which Arachne
descriptors refer to specific functions in the underlying codebase.

<?about :clojure/Namespace ?>

Class for RDF entities that have a 1:1 correspondence with a Clojure
namespace. The IRI of the entity should be of the form
`urn:clojure:<namespace>`, which Aristotle will automatically convert
back and forth to the corresponding Clojure symbol when reading or
writing RDF/EDN.

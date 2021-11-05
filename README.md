A simple script to materialise inferred triples from OWL ontologies.

## Build docker image

```sh
./build_docker.sh
```

## Usage

```sh
docker run -v "$PWD"/data:/data --rm org.dllearner.infgen/infgen /data/myonto.owl o:pellet
```

### options:

#### [OWLAPI](https://github.com/owlcs/owlapi) reasoners

- o:pellet
- o:hermit
- o:jfact
- o:elk
- o:fact

#### Jena [InfModel](https://jena.apache.org/documentation/javadoc/jena/org/apache/jena/ontology/OntModelSpec.html)

- J:rule
- J:micro_rule
- J:mini_rule
- J:rdfs

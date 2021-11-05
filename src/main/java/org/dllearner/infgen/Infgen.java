package org.dllearner.infgen;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.dllearner.core.ComponentInitException;
import org.dllearner.kb.LocalModelBasedSparqlEndpointKS;
import org.dllearner.kb.OWLFile;
import org.dllearner.reasoning.OWLAPIReasoner;
import org.dllearner.reasoning.ReasonerImplementation;
import org.dllearner.utilities.OwlApiJenaUtils;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class Infgen {
    private static void reasonWithHermit(String in, String out, boolean copy) throws IOException, OWLOntologyStorageException, OWLOntologyCreationException {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        File inputOntologyFile = new File(in);
        OWLOntology ontology = manager.loadOntologyFromOntologyDocument(inputOntologyFile);
        org.semanticweb.HermiT.ReasonerFactory factory
                = new org.semanticweb.HermiT.ReasonerFactory();
        // The factory can now be used to obtain an instance of HermiT as an OWLReasoner.
        org.semanticweb.HermiT.Configuration c
                = new org.semanticweb.HermiT.Configuration();
        OWLReasoner reasoner = factory.createReasoner(ontology, c);
        reasonWithOwlapi(manager, reasoner, ontology, copy, out);
    }

    private static void reasonWithOwlapi(OWLOntologyManager manager, OWLReasoner reasoner, OWLOntology ontology, boolean saveWithJena, String out) throws OWLOntologyCreationException, IOException, OWLOntologyStorageException {
        List<InferredAxiomGenerator<? extends OWLAxiom>> generators = new ArrayList<>();
        generators.add(new InferredSubClassAxiomGenerator());
        generators.add(new InferredClassAssertionAxiomGenerator());
        generators.add(new InferredDisjointClassesAxiomGenerator() {
            boolean precomputed = false;

            @Override
            protected void addAxioms(OWLClass entity, OWLReasoner reasoner, OWLDataFactory dataFactory, Set<OWLDisjointClassesAxiom> result) {
                if (!precomputed) {
                    reasoner.precomputeInferences(org.semanticweb.owlapi.reasoner.InferenceType.DISJOINT_CLASSES);
                    precomputed = true;
                }
                for (OWLClass cls : reasoner.getDisjointClasses(entity).getFlattened()) {
                    result.add(dataFactory.getOWLDisjointClassesAxiom(entity, cls));
                }
            }
        });
        generators.add(new InferredDataPropertyCharacteristicAxiomGenerator());
        generators.add(new InferredEquivalentClassAxiomGenerator());
        generators.add(new InferredEquivalentDataPropertiesAxiomGenerator());
        generators.add(new InferredEquivalentObjectPropertyAxiomGenerator());
        generators.add(new InferredInverseObjectPropertiesAxiomGenerator());
        generators.add(new InferredObjectPropertyCharacteristicAxiomGenerator());
        generators.add(new InferredPropertyAssertionGenerator());
        generators.add(new InferredSubDataPropertyAxiomGenerator());
        generators.add(new InferredSubObjectPropertyAxiomGenerator());

        InferredOntologyGenerator iog = new InferredOntologyGenerator(reasoner, generators);
        OWLOntology inferredAxiomsOntology = manager.createOntology();
        iog.fillOntology(manager.getOWLDataFactory(), inferredAxiomsOntology);
        if (saveWithJena) {
            manager.addAxioms(inferredAxiomsOntology, ontology.getAxioms());
            Model m1 = OwlApiJenaUtils.getModel(inferredAxiomsOntology);
            Model m0 = OwlApiJenaUtils.getModel(ontology);
            m0.add(m1.listStatements());
            m0.write(new FileOutputStream(out));
        } else {
            File inferredOntologyFile = new File(out);
            if (!inferredOntologyFile.exists())
                inferredOntologyFile.createNewFile();
            inferredOntologyFile = inferredOntologyFile.getAbsoluteFile();
            OutputStream outputStream = new FileOutputStream(inferredOntologyFile);
            manager.saveOntology(inferredAxiomsOntology, manager.getOntologyFormat(ontology), outputStream);
        }
        System.out.println("The ontology in " + out + " should now contain all inferred axioms ");
    }

    private static void loadThroughOwlFile(String in, String out, String reasoning) throws ComponentInitException, FileNotFoundException {
        OWLFile owlFile = new OWLFile(in);
        owlFile.setReasoningString(reasoning);
        owlFile.init();
        Model model = RDFDataMgr.loadModel(owlFile.getURL().getFile());
        System.err.println("file reasoning: " + ((owlFile.getReasoning() == null || owlFile.getReasoning().getReasonerFactory() == null) ? "(none)"
                : owlFile.getReasoning().getReasonerFactory().getURI()));
        OntModel ontModel = new LocalModelBasedSparqlEndpointKS(model, owlFile.getReasoning()).getModel();
        System.out.println("lto; size:" + ontModel.size());
        ontModel.writeAll(new FileOutputStream(out), "rdfxml", null);
    }

    private static void reasonWithOar(String in, String out, String reasoning, boolean saveWithJena) throws ComponentInitException, OWLOntologyCreationException, IOException, OWLOntologyStorageException {
        OWLFile owlFile = new OWLFile(in);
        owlFile.init();
        OWLAPIReasoner oaReasoner = new OWLAPIReasoner(owlFile);
        oaReasoner.setReasonerImplementation(ReasonerImplementation.valueOf(reasoning.toUpperCase(Locale.ROOT)));
        oaReasoner.init();
        OWLOntologyManager manager = oaReasoner.getManager();
        OWLReasoner reasoner = oaReasoner.getReasoner();
        OWLOntology ontology = reasoner.getRootOntology();
        reasonWithOwlapi(manager, reasoner, ontology, saveWithJena, out);
    }

    private static void loadThroughJena(String in, String out) throws OWLOntologyCreationException, FileNotFoundException {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(in));
        Model model = OwlApiJenaUtils.getModel(ontology);
        System.out.println("ltj; size:" + model.size());
        model.write(new FileOutputStream(out));
    }

    public static void main(String[] args) throws Exception {
        String in = args.length > 0 ? args[0] : "../examples/carcinogenesis/carcinogenesis.owl";
        if (args.length <= 1) {
            loadThroughJena(in, in + ".jena1");
            reasonWithHermit(in, in + ".her0", false);
        } else {
            for (int i = 0, j = 0; i < args[1].length(); ++i, ++j) {
                String step = in;
                String next = null;
                boolean saveWithJena = Character.isUpperCase(args[1].charAt(i));
                RSR fr = findReasoning(args[1], i + 1);
                switch (args[1].charAt(i)) {
                    case 'j':
                        next = buildNext(in, j, "j", true);
                        loadThroughJena(step, next);
                        break;
                    case 'J':
                        next = buildNext(in, j, "o" + fr.ext, true);
                        loadThroughOwlFile(step, next, fr.reasoning);
                        break;
                    case 'h':
                    case 'H':
                        next = buildNext(in, j, "her", saveWithJena);
                        reasonWithHermit(step, next, saveWithJena);
                        break;
                    case 'o':
                    case 'O':
                        next = buildNext(in, j, "oa" + fr.ext, saveWithJena);
                        reasonWithOar(step, next, fr.reasoning, saveWithJena);
                        break;
                    default:
                        System.err.println("Unknown mode: " + args[1].charAt(i));
                }
                i += fr.step;
                if (next != null) {
                    step = next;
                    next = null;
                }
            }
        }

    }

    public static RSR findReasoning(String arg, int i) {
        RSR ret = new RSR();
        ret.ext = "";
        ret.reasoning = "";
        ret.step = 0;

        if (i >= arg.length())
            return ret;

        if (arg.charAt(i) == ':') {
            String beg = arg.substring(i + 1);
            int nextDelim = beg.indexOf(':');
            if (nextDelim != -1) {
                ret.reasoning = beg.substring(0, nextDelim);
                ret.step = nextDelim + 2;
            } else {
                ret.reasoning = beg;
                ret.step = beg.length() + 1;
            }
        }
        if (!ret.reasoning.equals("")) {
            ret.ext = "_" + ret.reasoning;
        }
        return ret;
    }

    public static String buildNext(String in, int i, String tag, boolean saveWithJena) {
        return in + "." + i + "." + tag + "_" + (saveWithJena ? "J" : "O");
    }

}

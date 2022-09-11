
package de.rub.iib.shacl.benchmark;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.topbraid.shacl.rules.RuleUtil;
import org.topbraid.shacl.validation.ValidationUtil;
import org.topbraid.shacl.vocabulary.SH;


@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 2, jvmArgs = {"-Xms24G", "-Xmx24G"})
//@Warmup(iterations = 3)
//@Measurement(iterations = 8)
public class ShaclIcddBenchmarkExecution {
    private static final Logger logger = LoggerFactory.getLogger(ShaclIcddBenchmarkExecution.class);
    private static final Marker Benchmark_MARKER = MarkerFactory.getMarker("ShaclIcddBenchmark");

    @Param({"1", "2" , "3"  , "4"  }) 
    public static int DataSetNr;

    @Param({"1","2","3" }) 
    public static int RuleSetNr;

    @Param({"Binary", "DirectedBinary", "Directed1ToN"})  
    public static String LinkType; 

    public static Path path = Paths.get(".").toAbsolutePath().normalize();

    public static void main(String[] args) throws RunnerException {
        Path path = Paths.get(".").toAbsolutePath().normalize();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
        LocalDateTime now = LocalDateTime.now();
        String pathData = path.toFile().getAbsolutePath() + "/src/main/resources/benchmark/results/Benchmark_Results_"+dtf.format(now)+".json";
        //String pathData = path.toFile().getAbsolutePath() + "/src/main/resources/benchmark/results/Benchmark_Results.json";
        Options opt = new OptionsBuilder()
                .include(ShaclIcddBenchmarkExecution.class.getSimpleName())
                .forks(1)
                .resultFormat(ResultFormatType.JSON)
                .result(pathData)
                //.syncIterations(false)
                //threads(Runtime.getRuntime().availableProcessors())
                .warmupIterations(5).warmupTime(TimeValue.seconds(1))
                .measurementIterations(20).measurementTime(TimeValue.seconds(1))
                .build();
        new Runner(opt).run();
    }

    //@Benchmark
    public static void ExecuteLinkPredicates(DatasetState state) {
        state.InferenceModel = RuleUtil.executeRules(state.Data, state.Rules, state.InferenceModel, null);
    }

    @Benchmark
    public static void SHACLInferredDataset(InferredDataset state) {
        //state.Report = ValidationUtil.validateModel(state.DataGraph, state.ShapesGraph, true);
        state.ApacheReport = ShaclValidator.get().validate(state.ApacheShapesGraph, state.DataGraph.getGraph());
    }

    @Benchmark
    public static void SHACLNonInferredDataset(NonInferredDataset state) {
        //state.Report = ValidationUtil.validateModel(state.DataGraph, state.ShapesGraph, true);
        state.ApacheReport = ShaclValidator.get().validate(state.ApacheShapesGraph, state.DataGraph.getGraph());
    }

    @State(Scope.Thread)
    public static class DatasetState {
        Model Data, Rules, InferenceModel;

        @Setup(Level.Trial)
        public void setup() {
            Path path = Paths.get(".").toAbsolutePath().normalize();
            String data = "file:" + path.toFile().getAbsolutePath() + "/src/main/resources/benchmark/datasets/" + DataSetNr + "/dataset-" + LinkType + ".ttl";
            String shape = "file:" + path.toFile().getAbsolutePath() + "/src/main/resources/benchmark/rulesets/predicates_" + LinkType + ".ttl";

            Data = ModelFactory.createDefaultModel();
            Data.read(data);

            Rules = ModelFactory.createDefaultModel();
            Rules.read(shape);

            InferenceModel = ModelFactory.createDefaultModel();
        }

        @TearDown(Level.Trial)
        public void teardown() {
            Path path = Paths.get(".").toAbsolutePath().normalize();
            String inferences = path.toFile().getAbsolutePath() + "/src/main/resources/benchmark/datasets/" + DataSetNr + "/inferences-" + LinkType + ".ttl";
            File inferencesFile = new File(inferences);
            try {
                inferencesFile.createNewFile();
                OutputStream reportOutputStream2 = Files.newOutputStream(inferencesFile.toPath());
                RDFDataMgr.write(reportOutputStream2, InferenceModel, RDFFormat.TTL);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            Data = null;
            Rules = null;
            InferenceModel = null;
        }
    }

    @State(Scope.Thread)
    public static class InferredDataset {
        Model Inferences, DataGraph, ShapesGraph;
        Shapes ApacheShapesGraph;
        Resource Report;
        ValidationReport ApacheReport;

        @Setup(Level.Trial)
        public void setup() {
            Path path = Paths.get(".").toAbsolutePath().normalize();
            String data = "file:" + path.toFile().getAbsolutePath() + "/src/main/resources/benchmark/datasets/" + DataSetNr + "/dataset-" + LinkType + ".ttl";
            //String predicates = "file:" + path.toFile().getAbsolutePath() + "/src/main/resources/benchmark/rulesets/predicates.ttl";
            String shape = "file:" + path.toFile().getAbsolutePath() + "/src/main/resources/benchmark/rulesets/predicates_" + LinkType + ".ttl";

            Model dataModel = ModelFactory.createDefaultModel();
            dataModel.read(data);

            Model predicateRules = ModelFactory.createDefaultModel();
            predicateRules.read(shape);


            Map<String, String> prefixes = predicateRules.getNsPrefixMap();
            Inferences = ModelFactory.createDefaultModel();
            Inferences = RuleUtil.executeRules(dataModel, predicateRules, Inferences, null);
            Inferences.setNsPrefixes(prefixes);

            DataGraph = ModelFactory.createDefaultModel();
            DataGraph.add(dataModel);
            DataGraph.add(Inferences);
            DataGraph.setNsPrefixes(prefixes);

            String inferences = path.toFile().getAbsolutePath() + "/src/main/resources/benchmark/datasets/" + DataSetNr + "/inferences-" + LinkType + ".ttl";
            File inferencesFile = new File(inferences);
            try {
                inferencesFile.createNewFile();
                OutputStream reportOutputStream2 = Files.newOutputStream(inferencesFile.toPath());
                RDFDataMgr.write(reportOutputStream2, Inferences, RDFFormat.TTL);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            String validationShape = "file:" + path.toFile().getAbsolutePath() + "/src/main/resources/benchmark/rulesets/" + RuleSetNr + "/shapes-1.ttl";
            ShapesGraph = ModelFactory.createDefaultModel();
            ShapesGraph.read(validationShape);

            ApacheShapesGraph = Shapes.parse(ShapesGraph);
            logger.info(Benchmark_MARKER, "[Validation] ValidationShape size: <" + ShapesGraph.getGraph().size() + " Triples>");
            logger.info(Benchmark_MARKER, "[Validation] Action: Starting Validation with previous inferring.");
        }

        @TearDown(Level.Trial)
        public void teardown() {
            Inferences = null;
            DataGraph = null;
            ShapesGraph = null;

            try {
                Files.createDirectories(Paths.get(path.toFile().getAbsolutePath() + "/src/main/resources/benchmark/results/dataset_" + DataSetNr +"/type_" + LinkType +"/"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }


            if (Report != null) {
                boolean conforms = Report.getProperty(SH.conforms).getBoolean();
                logger.info(Benchmark_MARKER, "[Validation] Conforms = " + conforms);


                int errors = Report.listProperties(SH.result).toList().size();
                logger.info(Benchmark_MARKER, "[Validation] Validation Errors: " + errors);
                String report = path.toFile().getAbsolutePath() + "/src/main/resources/benchmark/results/dataset_" + DataSetNr +"/type_" + LinkType +"/ValidationResults-Ruleset" + RuleSetNr + "-Rule-inferred.ttl";
                File reportFile = new File(report);
                try {
                    reportFile.createNewFile();
                    OutputStream reportOutputStream2 = Files.newOutputStream(reportFile.toPath());

                    RDFDataMgr.write(reportOutputStream2, Report.getModel(), RDFFormat.TTL);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                Report = null;

            }
            if (ApacheReport != null) {
                try {
                    String report = path.toFile().getAbsolutePath() + "/src/main/resources/benchmark/results/dataset_" + DataSetNr +"/type_" + LinkType +"/ValidationResults-Ruleset" + RuleSetNr + "-Rule-inferred.ttl";
                    File reportFile = new File(report);
                    reportFile.createNewFile();
                    OutputStream reportOutputStream2 = Files.newOutputStream(reportFile.toPath());

                    RDFDataMgr.write(reportOutputStream2, ApacheReport.getModel(), Lang.TTL);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                ApacheReport = null;
            }
        }

    }

    @State(Scope.Thread)
    public static class NonInferredDataset {
        Model DataGraph, ShapesGraph;
        Resource Report;
        Shapes ApacheShapesGraph;
        ValidationReport ApacheReport;

        @Setup(Level.Trial)
        public void setup() {
            String data = "file:" + path.toFile().getAbsolutePath() + "/src/main/resources/benchmark/datasets/" + DataSetNr + "/dataset-" + LinkType + ".ttl";
            DataGraph = ModelFactory.createDefaultModel();
            DataGraph.read(data);

            String validationShape = "file:" + path.toFile().getAbsolutePath() + "/src/main/resources/benchmark/rulesets/" + RuleSetNr + "/shapes-2.ttl";
            ShapesGraph = ModelFactory.createDefaultModel();
            ShapesGraph.read(validationShape);
            ApacheShapesGraph = Shapes.parse(ShapesGraph);
            logger.error(Benchmark_MARKER, "[Validation] ValidationShape size: <" + ShapesGraph.getGraph().size() + " Triples>");
        }

        @TearDown(Level.Trial)
        public void teardown() {
            DataGraph = null;
            ShapesGraph = null;
            try {
                Files.createDirectories(Paths.get(path.toFile().getAbsolutePath() + "/src/main/resources/benchmark/results/dataset_" + DataSetNr +"/type_" + LinkType +"/"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (Report != null) {
                boolean conforms = Report.getProperty(SH.conforms).getBoolean();
                logger.info(Benchmark_MARKER, "[Validation] Conforms = " + conforms);


                int errors = Report.listProperties(SH.result).toList().size();
                logger.info(Benchmark_MARKER, "[Validation] Validation Errors: " + errors);
                Path path = Paths.get(".").toAbsolutePath().normalize();
                String report = path.toFile().getAbsolutePath() + "/src/main/resources/benchmark/results/ValidationResults_Dataset-" + DataSetNr + LinkType + "-Ruleset" + RuleSetNr + "-Rule-noninferred.ttl";
                File reportFile = new File(report);
                try {
                    reportFile.createNewFile();
                    OutputStream reportOutputStream2 = Files.newOutputStream(reportFile.toPath());

                    RDFDataMgr.write(reportOutputStream2, Report.getModel(), RDFFormat.TTL);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                Report = null;

            }
            if (ApacheReport != null) {
                try {
                    String report = path.toFile().getAbsolutePath() + "/src/main/resources/benchmark/results/dataset_" + DataSetNr +"/type_" + LinkType +"/ValidationResults-Ruleset" + RuleSetNr + "-Rule-noninferred.ttl";
                    File reportFile = new File(report);
                    if (reportFile.createNewFile()) {
                        OutputStream reportOutputStream2 = Files.newOutputStream(reportFile.toPath());
                        RDFDataMgr.write(reportOutputStream2, ApacheReport.getModel(), Lang.TTL);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                ApacheReport = null;
            }
        }
    }
}

  
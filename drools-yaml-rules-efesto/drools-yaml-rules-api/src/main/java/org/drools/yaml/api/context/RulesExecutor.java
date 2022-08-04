package org.drools.yaml.api.context;

import org.drools.core.facttemplates.Fact;
import org.drools.yaml.api.domain.RulesSet;
import org.drools.yaml.compilation.rulesmodel.PrototypeFactory;
import org.json.JSONObject;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.AgendaFilter;
import org.kie.api.runtime.rule.Match;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.drools.modelcompiler.facttemplate.FactFactory.createMapBasedFact;
import static org.drools.yaml.api.context.SessionGenerator.PROTOTYPE_NAME;

public class RulesExecutor {

    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    private final KieSession ksession;

    private final PrototypeFactory prototypeFactory;
    private final long id;

    private RulesExecutor(RulesSet rulesSet, long id) {
        this.prototypeFactory = new PrototypeFactory();
        this.ksession = SessionGenerator.INSTANCE.build(rulesSet, this);
        this.id = id;
    }



//    public static RulesExecutor createFromYaml(String yaml) {
//        return createFromYaml(RuleNotation.CoreNotation.INSTANCE, yaml);
//    }

//    public static RulesExecutor createFromYaml(RuleNotation notation, String yaml) {
//        return create(RuleFormat.YAML, notation, yaml);
//    }

//    public static RulesExecutor createFromJson(String json) {
//        return createFromJson(RuleNotation.CoreNotation.INSTANCE, json);
//    }
//
//    public static RulesExecutor createFromJson(RuleNotation notation, String json) {
//        return create(RuleFormat.JSON, notation, json);
//    }
//
//    private static RulesExecutor create(RuleFormat format, RuleNotation notation, String text) {
//        return createRulesExecutor( notation.toRulesSet( format, text ) );
//    }
//
    public static RulesExecutor createRulesExecutor(RulesSet rulesSet) {
        RulesExecutor rulesExecutor = new RulesExecutor( rulesSet, ID_GENERATOR.getAndIncrement());
        RulesExecutorContainer.INSTANCE.register(rulesExecutor);
        return rulesExecutor;
    }

    public long getId() {
        return id;
    }

    public PrototypeFactory getPrototypeFactory() {
        return prototypeFactory;
    }

    public void dispose() {
        RulesExecutorContainer.INSTANCE.dispose(this);
        ksession.dispose();
    }

    public long rulesCount() {
        return ksession.getKieBase().getKiePackages().stream().mapToLong(p -> p.getRules().size()).sum();
    }

    public int execute(String json) {
        return execute( new JSONObject(json).toMap() );
    }

    public int execute(Map<String, Object> factMap) {
        processFact( factMap );
        return ksession.fireAllRules();
    }

    public List<Match> process(String json) {
        return process( new JSONObject(json).toMap() );
    }

    public List<Match> process(Map<String, Object> factMap) {
        processFacts( factMap );
        RegisterOnlyAgendaFilter filter = new RegisterOnlyAgendaFilter();
        ksession.fireAllRules(filter);
        return filter.getMatchedRules();
    }

    private void processFacts(Map<String, Object> factMap) {
        if (factMap.size() == 1 && factMap.containsKey("facts")) {
            ((List<Map<String, Object>>)factMap.get("facts")).forEach(this::processFacts);
        } else {
            processFact(factMap);
        }
    }

    public void processFact(Map<String, Object> factMap) {
        ksession.insert( mapToFact(factMap) );
    }

    public boolean retract(String json) {
        return retractFact( new JSONObject(json).toMap() );
    }

    public boolean retractFact(Map<String, Object> factMap) {
        Fact toBeRetracted = mapToFact(factMap);

        return ksession.getFactHandles(o -> o instanceof Fact && Objects.equals(((Fact) o).asMap(), toBeRetracted.asMap()))
                .stream().findFirst()
                .map( fh -> {
                    ksession.delete( fh );
                    return true;
                }).orElse(false);
    }

    private Fact mapToFact(Map<String, Object> factMap) {
        Fact fact = createMapBasedFact( prototypeFactory.getPrototype(PROTOTYPE_NAME) );
        populateFact(fact, factMap, "");
        return fact;
    }

    private void populateFact(Fact fact, Map<?, ?> value, String fieldName) {
        for (Map.Entry entry : value.entrySet()) {
            String key = fieldName + entry.getKey();
            if (entry.getValue() instanceof Map) {
                populateFact(fact, (Map) entry.getValue(), key + ".");
            } else {
                fact.set(key, entry.getValue());
            }
        }
    }

    public Collection<? extends Object> getAllFacts() {
        return ksession.getObjects();
    }

    public List<Map<String, Object>> getAllFactsAsMap() {
        return getAllFacts().stream().map(Fact.class::cast).map(Fact::asMap).collect(Collectors.toList());
    }

    private static class RegisterOnlyAgendaFilter implements AgendaFilter {

        private final Set<Match> matchedRules = new LinkedHashSet<>();

        @Override
        public boolean accept(Match match) {
            matchedRules.add(match);
            return false;
        }

        public List<Match> getMatchedRules() {
            return new ArrayList<>( matchedRules );
        }
    }
}

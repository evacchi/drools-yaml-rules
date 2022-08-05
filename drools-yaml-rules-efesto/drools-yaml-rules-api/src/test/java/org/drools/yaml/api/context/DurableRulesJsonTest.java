package org.drools.yaml.api.context;

import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import org.drools.yaml.api.context.RulesExecutor;
import org.drools.yaml.api.domain.RuleMatch;
import org.drools.yaml.api.notations.DurableNotation;
import org.junit.Assert;
import org.junit.Test;
import org.kie.api.runtime.rule.Match;

import static io.smallrye.common.constraint.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

@QuarkusTest
public class DurableRulesJsonTest {

    private static final String DURABLE_RULES_JSON =
            "{\n" +
            "   \"myrules\":{\n" +
            "      \"R1\":{\n" +
            "         \"all\":[\n" +
            "            {\n" +
            "               \"m\":{\n" +
            "                  \"$lt\":{\n" +
            "                     \"sensu.data.i\": 2\n" +
            "                  }\n" +
            "               }\n" +
            "            }\n" +
            "         ],\n" +
            "         \"run\": \"exec first\"\n" +
            "      },\n" +
            "      \"R2\":{\n" +
            "         \"all\":[\n" +
            "            {\n" +
            "               \"first\":{\n" +
            "                  \"sensu.data.i\": 3\n" +
            "               },\n" +
            "               \"second\":{\n" +
            "                  \"j\": 2\n" +
            "               }\n" +
            "            }\n" +
            "         ],\n" +
            "         \"run\": \"exec second\"\n" +
            "      },\n" +
            "      \"R3\":{\n" +
            "         \"any\":[\n" +
            "            {\n" +
            "               \"all\":[\n" +
            "                  {\n" +
            "                     \"first\":{\n" +
            "                        \"sensu.data.i\": 3\n" +
            "                     },\n" +
            "                     \"second\":{\n" +
            "                        \"j\": 2\n" +
            "                     }\n" +
            "                  }\n" +
            "               ]\n" +
            "            },\n" +
            "            {\n" +
            "               \"all\":[\n" +
            "                  {\n" +
            "                     \"first\":{\n" +
            "                        \"sensu.data.i\": 4\n" +
            "                     },\n" +
            "                     \"second\":{\n" +
            "                        \"j\": 3\n" +
            "                     }\n" +
            "                  }\n" +
            "               ]\n" +
            "            }\n" +
            "         ],\n" +
            "         \"run\": \"exec third\"\n" +
            "      }\n" +
            "   }\n" +
            "}";

    @Test
    public void testExecuteRules() {
        RulesExecutor rulesExecutor = RulesExecutor.createFromJson(DurableNotation.INSTANCE, DURABLE_RULES_JSON);

        List<Match> matchedRules = rulesExecutor.processFacts( "{ \"sensu\": { \"data\": { \"i\":1 } } }" );
        assertEquals( 1, matchedRules.size() );
        assertEquals( "R1", matchedRules.get(0).getRule().getName() );

        matchedRules = rulesExecutor.processFacts( "{ facts: [ { \"sensu\": { \"data\": { \"i\":3 } } }, { \"j\":3 } ] }" );
        assertEquals( 0, matchedRules.size() );

        matchedRules = rulesExecutor.processFacts( "{ \"sensu\": { \"data\": { \"i\":4 } } }" );
        assertEquals( 1, matchedRules.size() );

        RuleMatch ruleMatch = RuleMatch.from( matchedRules.get(0) );
        Assert.assertEquals( "R3", ruleMatch.getRuleName() );
        Assert.assertEquals( 3, ((Map) ruleMatch.getFacts().get("second")).get("j") );

        assertEquals( 4, ((Map) ((Map) ((Map) ruleMatch.getFacts().get("first")).get("sensu")).get("data")).get("i") );

        rulesExecutor.dispose();
    }

    @Test
    public void testProcessWithAnd() {
        String jsonRule = "{ \"rules\": {\"r_0\": {\"all\": [{\"m_0\": {\"payload.provisioningState\": \"Succeeded\"}}, {\"m_1\": {\"payload.provisioningState\": \"Deleted\"}}]}}}";

        RulesExecutor rulesExecutor = RulesExecutor.createFromJson(DurableNotation.INSTANCE, jsonRule);

        List<Match> matchedRules = rulesExecutor.processFacts( "{ \"payload\": { \"provisioningState\": \"Succeeded\" } }" );
        assertEquals( 0, matchedRules.size() );

        matchedRules = rulesExecutor.processFacts( "{ \"payload\": { \"provisioningState\": \"Deleted\" } }" );
        assertEquals( 1, matchedRules.size() );
    }

    @Test
    public void testProcessWithExists() {
        String jsonRule = "{ \"rules\": {\"r_0\": {\"all\": [{\"m\": {\"$ex\": {\"subject.x\": 1}}}]}, \"r_1\": {\"all\": [{\"m\": {\"$nex\": {\"subject.x\": 1}}}]}}}";

        RulesExecutor rulesExecutor = RulesExecutor.createFromJson(DurableNotation.INSTANCE, jsonRule);
        List<Match> matchedRules = rulesExecutor.processFacts( "{ \"subject\": { \"y\": \"Succeeded\" } }" );
        assertEquals( 1, matchedRules.size() );
        assertEquals( "r_1", matchedRules.get(0).getRule().getName() );

        matchedRules = rulesExecutor.processFacts( "{ \"subject\": { \"x\": null } }" );
        assertEquals( 1, matchedRules.size() );
        assertEquals( "r_0", matchedRules.get(0).getRule().getName() );
    }

    @Test
    public void testProcessWithNestedValues() {
        String jsonRule = "{ \"rules\": {\"r_0\": {\"all\": [{\"m\": {\"subject\": {\"x\": \"Kermit\"}, \"predicate\": \"eats\", \"object\": \"flies\"}}]}}}";

        RulesExecutor rulesExecutor = RulesExecutor.createFromJson(DurableNotation.INSTANCE, jsonRule);
        List<Match> matchedRules = rulesExecutor.processFacts( "{ \"subject\": { \"x\": \"Kermit\" }, \"predicate\": \"eats\", \"object\": \"flies\" }" );
        assertEquals( 1, matchedRules.size() );
    }

    @Test
    public void testProcessWithAndConstraint() {
        String jsonRule = "{ \"rules\": {\"r_0\": {\"all\": [{\"m\": {\"$and\": [{\"nested.i\" : 1}, {\"nested.j\" : 2}]}}]}}}";

        RulesExecutor rulesExecutor = RulesExecutor.createFromJson(DurableNotation.INSTANCE, jsonRule);

        List<Match> matchedRules = rulesExecutor.processFacts( "{ \"nested\": { \"i\": 1 } }" );
        assertEquals( 0, matchedRules.size() );

        matchedRules = rulesExecutor.processFacts( "{ \"nested\": { \"i\": 1, \"j\": 2 } }" );
        assertEquals( 1, matchedRules.size() );
    }

    @Test
    public void testProcessWithOrConstraint() {
        String jsonRule = "{ \"rules\": {\"r_0\": {\"all\": [{\"m\": {\"$or\": [{\"nested.i\" : 1}, {\"nested.j\" : 2}]}}]}}}";

        RulesExecutor rulesExecutor = RulesExecutor.createFromJson(DurableNotation.INSTANCE, jsonRule);

        List<Match> matchedRules = rulesExecutor.processFacts( "{ \"nested\": { \"i\": 1 } }" );
        assertEquals( 1, matchedRules.size() );
    }

    @Test
    public void testRetract() {
        String jsonRule = "{ \"rules\": {\"r_0\": {\"all\": [{\"m\": {\"nested.i\" : 1}}]}}}";

        RulesExecutor rulesExecutor = RulesExecutor.createFromJson(DurableNotation.INSTANCE, jsonRule);

        List<Match> matchedRules = rulesExecutor.processFacts( "{ \"nested\": { \"i\": 1 } }" );
        assertEquals( 1, matchedRules.size() );

        assertTrue( rulesExecutor.retract( "{ \"nested\": { \"i\": 1 } }" ) );
    }

    @Test
    public void testProcessWithAddConstraint() {
        String jsonRule =
                "{ \"rules\": {\"r_0\": {\"all\": [{\"m\": {\"nested.i\": {\"$add\": {\"$l\": {\"$m\": \"nested.j\"}, \"$r\": 1}}}}]}}}";

        RulesExecutor rulesExecutor = RulesExecutor.createFromJson(DurableNotation.INSTANCE, jsonRule);

        List<Match> matchedRules = rulesExecutor.processFacts( "{ \"nested\": { \"i\": 2, \"j\":1 } }" );
        assertEquals( 1, matchedRules.size() );
    }

    @Test
    public void testProcessWithSubConstraint() {
        String jsonRule =
                "{ \"rules\": {\"r_0\": {\"all\": [{\"m\": {\"nested.i\": {\"$sub\": {\"$l\": {\"$m\": \"nested.j\"}, \"$r\": 1}}}}]}}}";

        RulesExecutor rulesExecutor = RulesExecutor.createFromJson(DurableNotation.INSTANCE, jsonRule);

        List<Match> matchedRules = rulesExecutor.processFacts( "{ \"nested\": { \"i\": 1, \"j\":2 } }" );
        assertEquals( 1, matchedRules.size() );
    }

    @Test
    public void testGetAllFacts() {
        String jsonRule = "{ \"rules\": {\"r_0\": {\"all\": [{\"m\": {\"nested.i\" : 1}}]}}}";

        RulesExecutor rulesExecutor = RulesExecutor.createFromJson(DurableNotation.INSTANCE, jsonRule);

        List<Match> matchedRules = rulesExecutor.processFacts( "{ \"nested\": { \"i\": 1 } }" );
        assertEquals( 1, matchedRules.size() );
        matchedRules = rulesExecutor.processFacts( "{ \"nested\": { \"j\": 1 } }" );
        assertEquals( 0, matchedRules.size() );

        assertEquals( 2, rulesExecutor.getAllFactsAsMap().size() );
    }

    @Test
    public void testProcessWithBindingJoin() {
        String jsonRule =
                "{ \"rules\": {\"r_0\": {\"all\": [{\"first\": {\"i\": 0}}, {\"second\": {\"i\": {\"first\": \"j\"}}}]}}}";

        RulesExecutor rulesExecutor = RulesExecutor.createFromJson(DurableNotation.INSTANCE, jsonRule);

        List<Match> matchedRules = rulesExecutor.processFacts( "{ \"i\": 3 }" );
        assertEquals( 0, matchedRules.size() );

        matchedRules = rulesExecutor.processFacts( "{ \"i\": 0, \"j\": 3 }" );
        assertEquals( 1, matchedRules.size() );
    }

    @Test
    public void testProcessEventsWithBindingJoin() {
        String jsonRule =
                "{ \"rules\": {\"r_0\": {\"all\": [{\"first\": {\"i\": 0}}, {\"second\": {\"i\": {\"first\": \"j\"}}}]}}}";

        RulesExecutor rulesExecutor = RulesExecutor.createFromJson(DurableNotation.INSTANCE, jsonRule);

        // an event is immediately retracted ...
        List<Match> matchedRules = rulesExecutor.processEvents( "{ \"i\": 0, \"j\": 3 }" );
        assertEquals( 0, matchedRules.size() );

        // ... so it cannot join with a subsequently inserted fact
        matchedRules = rulesExecutor.processFacts( "{ \"i\": 3 }" );
        assertEquals( 0, matchedRules.size() );

        // the new event joins with the existing fact
        matchedRules = rulesExecutor.processFacts( "{ \"i\": 0, \"j\": 3 }" );
        assertEquals( 1, matchedRules.size() );
    }

    @Test
    public void testProcessWithBindingJoinAddConstraint() {
        String jsonRule =
                "{ \"rules\": {\"r_0\": {\"all\": [" +
                        "{\"first\": {\"i\": 0}}, " +
                        "{\"second\": {\"i\": 1}}, " +
                        "{\"third\": {\"i\": {\"$add\": {\"$l\": {\"first\": \"i\"}, \"$r\": 2}}}}]}}}";

        RulesExecutor rulesExecutor = RulesExecutor.createFromJson(DurableNotation.INSTANCE, jsonRule);

        List<Match> matchedRules = rulesExecutor.processFacts( "{ \"i\": 0, \"j\": 3 }" );
        assertEquals( 0, matchedRules.size() );

        matchedRules = rulesExecutor.processFacts( "{ \"i\": 1 }" );
        assertEquals( 0, matchedRules.size() );

        matchedRules = rulesExecutor.processFacts( "{ \"i\": 2 }" );
        assertEquals( 1, matchedRules.size() );
    }

    @Test
    public void testOr() {
        String jsonRule =
                "{ \"rules\": {\"r_0\": {\"all\": [{\"m\": {\"$or\": [{\"nested.i\": 1}, {\"nested.j\": 2}]}}]}}}";

        RulesExecutor rulesExecutor = RulesExecutor.createFromJson(DurableNotation.INSTANCE, jsonRule);

        List<Match> matchedRules = rulesExecutor.processFacts( "{ \"nested\": { \"i\": 2, \"j\":1 } }" );
        assertEquals( 0, matchedRules.size() );

        matchedRules = rulesExecutor.processFacts( "{ \"nested\": { \"j\":2 } }" );
        assertEquals( 1, matchedRules.size() );

        matchedRules = rulesExecutor.processFacts( "{ \"nested\": { \"i\": 1, \"j\":2 } }" );
        assertEquals( 1, matchedRules.size() );
    }
}

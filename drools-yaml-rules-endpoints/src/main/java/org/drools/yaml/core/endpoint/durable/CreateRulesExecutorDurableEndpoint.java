package org.drools.yaml.core.endpoint.durable;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.drools.yaml.api.context.RulesExecutor;
import org.drools.yaml.api.context.RulesExecutorContainer;
import org.drools.yaml.api.domain.RulesSet;
import org.drools.yaml.api.domain.durable.DurableRules;
import org.drools.yaml.compilation.RulesCompilationContext;
import org.drools.yaml.compilation.model.RuleSetResource;
import org.kie.efesto.compilationmanager.api.service.CompilationManager;
import org.kie.memorycompiler.KieMemoryCompiler;

import java.util.concurrent.atomic.AtomicLong;

@Path("/create-durable-rules-executor")
public class CreateRulesExecutorDurableEndpoint {
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);
    private static final CompilationManager compilationManager =
            org.kie.efesto.compilationmanager.api.utils.SPIUtils.getCompilationManager(true).get();

    @POST()
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public long createRuleBase(DurableRules durableRules) {
        long toReturn = ID_GENERATOR.getAndAdd(1);
        String basePath = "/drl/ruleset/" + toReturn;
        RulesSet rulesSet = durableRules.toRulesSet();
        RuleSetResource ruleSetResource = new RuleSetResource(rulesSet, basePath);

        RulesCompilationContext rulesCompilationContext =
                new RulesCompilationContext(
                        new KieMemoryCompiler.MemoryCompilerClassLoader(
                                Thread.currentThread().getContextClassLoader()));

        compilationManager.processResource(rulesCompilationContext, ruleSetResource);

        RulesExecutor rulesExecutor = rulesCompilationContext.getRulesExecutor();
        RulesExecutorContainer.INSTANCE.register(rulesExecutor);

        return toReturn;
    }
}

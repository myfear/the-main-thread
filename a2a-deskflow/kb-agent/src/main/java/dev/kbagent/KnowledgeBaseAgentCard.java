package dev.kbagent;

import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.a2a.server.PublicAgentCard;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import io.a2a.spec.TransportProtocol;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class KnowledgeBaseAgentCard {

        @ConfigProperty(name = "deskflow.a2a.public-base-url")
        String publicBaseUrl;

        @Produces
        @PublicAgentCard
        public AgentCard agentCard() {
                String base = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                                : publicBaseUrl;
                String jsonRpcUrl = base + "/";
                return new AgentCard.Builder()
                                .name("DeskFlow Knowledge Base Agent")
                                .description(
                                                """
                                                                Returns Level-1 remediation steps for common enterprise
                                                                IT support categories including VPN, email, identity,
                                                                performance, software, and peripherals.
                                                                """)
                                .url(jsonRpcUrl)
                                .version("1.0.0")
                                .capabilities(
                                                new AgentCapabilities.Builder()
                                                                .streaming(false)
                                                                .pushNotifications(false)
                                                                .stateTransitionHistory(false)
                                                                .build())
                                .defaultInputModes(List.of("text"))
                                .defaultOutputModes(List.of("text"))
                                .preferredTransport(TransportProtocol.JSONRPC.asString())
                                .skills(
                                                List.of(
                                                                new AgentSkill.Builder()
                                                                                .id("kb-remediation")
                                                                                .name("IT Remediation Lookup")
                                                                                .description(
                                                                                                "Returns actionable remediation steps given a ticket category, severity, summary, and details")
                                                                                .tags(List.of("itsm", "helpdesk",
                                                                                                "remediation",
                                                                                                "knowledge-base"))
                                                                                .examples(
                                                                                                List.of(
                                                                                                                "VPN not connecting after OS update",
                                                                                                                "User locked out of SSO following password expiry"))
                                                                                .build()))
                                .build();
        }
}

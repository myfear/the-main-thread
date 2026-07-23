package fernbank.admission

import rego.v1

internal_manifest := {
    "skill_id": "docs_generate",
    "publisher": "internal:docs-platform",
    "publisher_trust_tier": "internal-verified",
    "signature_verified": true,
    "requested_scopes": ["context:read", "filesystem:write"],
    "declared_capabilities": ["document-generation"],
    "allowed_teams": ["content", "platform"],
}

third_party_manifest := {
    "skill_id": "pptx_export",
    "publisher": "third-party:acme-skills",
    "publisher_trust_tier": "third-party-unverified",
    "signature_verified": true,
    "requested_scopes": ["filesystem:write", "network:egress", "context:read"],
    "declared_capabilities": ["document-generation"],
    "allowed_teams": ["content"],
}

unsigned_manifest := {
    "skill_id": "unsigned_status",
    "publisher": "internal:ops-lab",
    "publisher_trust_tier": "internal-unverified",
    "signature_verified": false,
    "requested_scopes": ["context:read"],
    "declared_capabilities": ["status-reporting"],
    "allowed_teams": ["platform"],
}

content_subject := {
    "principal_name": "alice",
    "roles": ["content"],
}

platform_subject := {
    "principal_name": "bob",
    "roles": ["platform"],
}

test_internal_verified_skill_is_allowed_in_prod if {
    result := decision with input as {
        "action": "mcp:tool:access",
        "runtime_environment": "prod",
        "skill": internal_manifest,
        "subject": content_subject,
    }
    result.allow
    count(result.reasons) == 0
}

test_third_party_write_scope_is_denied_in_prod if {
    result := decision with input as {
        "action": "mcp:tool:access",
        "runtime_environment": "prod",
        "skill": third_party_manifest,
        "subject": content_subject,
    }
    not result.allow
    "SCOPE_NOT_ALLOWED" in {reason.code | some reason in result.reasons}
}

test_missing_signature_is_denied_in_prod if {
    result := decision with input as {
        "action": "mcp:tool:access",
        "runtime_environment": "prod",
        "skill": unsigned_manifest,
        "subject": platform_subject,
    }
    not result.allow
    "PROD_SIGNATURE_REQUIRED" in {reason.code | some reason in result.reasons}
}

test_third_party_write_scope_is_soft_flagged_in_dev if {
    result := decision with input as {
        "action": "mcp:tool:access",
        "runtime_environment": "dev",
        "skill": third_party_manifest,
        "subject": content_subject,
    }
    result.allow
    "SCOPE_SOFT_FLAG" in {warning.code | some warning in result.warnings}
}

test_team_boundary_is_enforced_in_dev if {
    result := decision with input as {
        "action": "mcp:tool:access",
        "runtime_environment": "dev",
        "skill": third_party_manifest,
        "subject": platform_subject,
    }
    not result.allow
    "TEAM_NOT_AUTHORIZED" in {reason.code | some reason in result.reasons}
}

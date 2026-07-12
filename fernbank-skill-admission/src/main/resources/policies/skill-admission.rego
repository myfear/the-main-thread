package fernbank.admission

import rego.v1

policy_version := "2026-07-12"

default allow := false

allowed_scopes := {
    "internal-verified": {
        "context:read",
        "database:read",
        "filesystem:read",
        "filesystem:write",
        "network:egress",
    },
    "internal-unverified": {
        "context:read",
        "filesystem:read",
    },
    "third-party-verified": {
        "context:read",
        "filesystem:read",
    },
    "third-party-unverified": {
        "context:read",
    },
}

known_trust_tier if {
    allowed_scopes[input.skill.publisher_trust_tier]
}

scope_allowed(scope) if {
    scope in allowed_scopes[input.skill.publisher_trust_tier]
}

team_allowed if {
    input.subject.team in input.skill.allowed_teams
}

deny contains {
    "code": "TEAM_NOT_AUTHORIZED",
    "message": sprintf("team %q is not allowed to use this skill", [input.subject.team]),
} if {
    not team_allowed
}

deny contains {
    "code": "UNKNOWN_TRUST_TIER",
    "message": sprintf("publisher trust tier %q is not configured", [input.skill.publisher_trust_tier]),
} if {
    not known_trust_tier
}

deny contains {
    "code": "PROD_SIGNATURE_REQUIRED",
    "message": "production requires a verified skill signature",
} if {
    input.runtime_environment == "prod"
    not input.skill.signature_verified
}

deny contains {
    "code": "SCOPE_NOT_ALLOWED",
    "message": sprintf("scope %q is not allowed for trust tier %q", [scope, input.skill.publisher_trust_tier]),
    "scope": scope,
} if {
    input.runtime_environment == "prod"
    some scope in input.skill.requested_scopes
    not scope_allowed(scope)
}

warn contains {
    "code": "SIGNATURE_SOFT_FLAG",
    "message": "non-production environment accepted an unverified signature",
} if {
    input.runtime_environment != "prod"
    not input.skill.signature_verified
}

warn contains {
    "code": "SCOPE_SOFT_FLAG",
    "message": sprintf("non-production environment accepted scope %q outside the tier allowlist", [scope]),
    "scope": scope,
} if {
    input.runtime_environment != "prod"
    some scope in input.skill.requested_scopes
    not scope_allowed(scope)
}

allow if count(deny) == 0

outcome := "allow" if allow else := "deny"

enforcement_mode := "enforce" if input.runtime_environment == "prod" else := "warn"

decision := {
    "allow": allow,
    "enforcement_mode": enforcement_mode,
    "outcome": outcome,
    "policy_version": policy_version,
    "reasons": [reason | some reason in deny],
    "warnings": [warning | some warning in warn],
}

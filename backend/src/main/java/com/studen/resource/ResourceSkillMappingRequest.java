package com.studen.resource;

import java.util.List;
import java.util.UUID;

// Full replace, mirrors RoleSkillsRequest's PUT-replace-all convention — skillIds may include or
// omit the resource's own primary skill id; the server silently ignores it either way since the
// primary skill is already implicitly mapped.
public record ResourceSkillMappingRequest(List<UUID> skillIds) {
}

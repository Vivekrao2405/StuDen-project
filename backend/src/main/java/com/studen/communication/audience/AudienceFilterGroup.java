package com.studen.communication.audience;

import java.util.List;

/**
 * An AND/OR group of child nodes (groups or conditions), letting the builder UI nest logic
 * arbitrarily deep. An empty {@code children} list always means "match every user" regardless of
 * operator — a freshly created campaign with no conditions yet targets everyone, rather than
 * inheriting AND's vacuous-true / OR's vacuous-false distinction, which would be surprising to a
 * non-technical admin.
 */
public record AudienceFilterGroup(AudienceLogicOperator operator, List<AudienceFilterNode> children)
        implements AudienceFilterNode {
}

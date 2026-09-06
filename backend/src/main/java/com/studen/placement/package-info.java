/**
 * Placement system foundation: roles, companies, role-skill mappings, placement profiles,
 * role-specific assessments, attempts, skill scores, prep series/modules and student progress.
 *
 * <p>Everything here is data-driven on purpose. The relationship
 * Role -&gt; Required Skills -&gt; Assessment -&gt; Skill Scores -&gt; Skill Gaps -&gt; Learning
 * Resources -&gt; Placement Prep is expressed entirely in this schema, so no frontend ever needs to
 * hard-code which skills a role requires or how readiness is made up.
 *
 * <p>This package owns no duplicate infrastructure. Identity and authorization come from
 * {@code com.studen.user}/{@code com.studen.security}, skills from {@code com.studen.skill},
 * MCQ-shaped questions from {@code com.studen.questionbank}, coding and other practical work from
 * {@code com.studen.practical}, and learning resources from {@code com.studen.resource} — which
 * already carries the Skill -&gt; Learning Resources relation the placement system needs.
 */
package com.studen.placement;

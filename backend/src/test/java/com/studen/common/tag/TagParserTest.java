package com.studen.common.tag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TagParserTest {

    @Test
    void parsesThreeSegmentTag() {
        ParsedTag parsed = TagParser.parse("python-lists-loops");
        assertThat(parsed.original()).isEqualTo("python-lists-loops");
        assertThat(parsed.language()).isEqualTo("python");
        assertThat(parsed.topics()).containsExactly("lists", "loops");
    }

    @Test
    void parsesFourSegmentTagWithoutMergingTrailingTopics() {
        ParsedTag parsed = TagParser.parse("python-lists-loops-references");
        assertThat(parsed.original()).isEqualTo("python-lists-loops-references");
        assertThat(parsed.language()).isEqualTo("python");
        assertThat(parsed.topics()).containsExactly("lists", "loops", "references");
    }

    @Test
    void parsesJavaArraysHashing() {
        ParsedTag parsed = TagParser.parse("java-arrays-hashing");
        assertThat(parsed.language()).isEqualTo("java");
        assertThat(parsed.topics()).containsExactly("arrays", "hashing");
    }

    @Test
    void parsesSqlJoinsWindowFunctionsAsThreeIndividualTopics() {
        // Pure hyphen split, per convention: every segment after the language is its own topic —
        // "window" and "functions" are never re-merged into "window-functions".
        ParsedTag parsed = TagParser.parse("sql-joins-window-functions");
        assertThat(parsed.language()).isEqualTo("sql");
        assertThat(parsed.topics()).containsExactly("joins", "window", "functions");
    }

    @Test
    void parsesPowerbiDaxDataModelingAsThreeIndividualTopics() {
        ParsedTag parsed = TagParser.parse("powerbi-dax-data-modeling");
        assertThat(parsed.language()).isEqualTo("powerbi");
        assertThat(parsed.topics()).containsExactly("dax", "data", "modeling");
    }

    @Test
    void singleSegmentTagHasLanguageAndNoTopics() {
        ParsedTag parsed = TagParser.parse("python");
        assertThat(parsed.original()).isEqualTo("python");
        assertThat(parsed.language()).isEqualTo("python");
        assertThat(parsed.topics()).isEmpty();
    }

    @Test
    void emptyTagHasNoLanguageAndNoTopics() {
        ParsedTag parsed = TagParser.parse("");
        assertThat(parsed.original()).isEqualTo("");
        assertThat(parsed.language()).isNull();
        assertThat(parsed.topics()).isEmpty();
    }

    @Test
    void blankTagHasNoLanguageAndNoTopics() {
        ParsedTag parsed = TagParser.parse("   ");
        assertThat(parsed.language()).isNull();
        assertThat(parsed.topics()).isEmpty();
    }

    @Test
    void nullTagReturnsAllNullsSafely() {
        ParsedTag parsed = TagParser.parse(null);
        assertThat(parsed.original()).isNull();
        assertThat(parsed.language()).isNull();
        assertThat(parsed.topics()).isEmpty();
    }

    @Test
    void malformedTagWithOnlyHyphensHasNoLanguageAndNoTopics() {
        ParsedTag parsed = TagParser.parse("---");
        assertThat(parsed.original()).isEqualTo("---");
        assertThat(parsed.language()).isNull();
        assertThat(parsed.topics()).isEmpty();
    }

    @Test
    void malformedTagWithLeadingTrailingAndDoubleHyphensSkipsBlankSegments() {
        ParsedTag parsed = TagParser.parse("-python--lists-");
        assertThat(parsed.language()).isEqualTo("python");
        assertThat(parsed.topics()).containsExactly("lists");
    }

    @Test
    void duplicateTopicsAreDeduplicatedPreservingFirstOccurrenceOrder() {
        ParsedTag parsed = TagParser.parse("python-lists-loops-lists");
        assertThat(parsed.language()).isEqualTo("python");
        assertThat(parsed.topics()).containsExactly("lists", "loops");
    }

    @Test
    void normalizesCaseAndWhitespace() {
        ParsedTag parsed = TagParser.parse("  Python-Lists-LOOPS  ");
        assertThat(parsed.language()).isEqualTo("python");
        assertThat(parsed.topics()).containsExactly("lists", "loops");
    }
}

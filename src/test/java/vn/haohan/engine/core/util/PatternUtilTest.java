package vn.haohan.engine.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.*;

class PatternUtilTest {

    @Test
    @DisplayName("Verify namespaced ID pattern validation")
    void testNamespacedId() {
        assertTrue(PatternUtil.isValidNamespacedId("lunar:void_stalker"));
        assertTrue(PatternUtil.isValidNamespacedId("skill_1"));
        assertTrue(PatternUtil.isValidNamespacedId("a"));
        assertTrue(PatternUtil.isValidNamespacedId("mob.type-1:variant"));

        assertFalse(PatternUtil.isValidNamespacedId(null));
        assertFalse(PatternUtil.isValidNamespacedId(""));
        assertFalse(PatternUtil.isValidNamespacedId("-invalid"));
        assertFalse(PatternUtil.isValidNamespacedId("invalid-"));
        assertFalse(PatternUtil.isValidNamespacedId("Invalid_Case"));
        assertFalse(PatternUtil.isValidNamespacedId("has space"));
    }

    @Test
    @DisplayName("Verify config ID pattern validation")
    void testConfigId() {
        assertTrue(PatternUtil.isValidConfigId("custom_drop_table"));
        assertTrue(PatternUtil.isValidConfigId("mob-1"));
        assertTrue(PatternUtil.isValidConfigId("test.file"));

        assertFalse(PatternUtil.isValidConfigId(null));
        assertFalse(PatternUtil.isValidConfigId(""));
        assertFalse(PatternUtil.isValidConfigId("lunar:with_colon")); // config ID does not allow ':'
        assertFalse(PatternUtil.isValidConfigId("-bad"));
        assertFalse(PatternUtil.isValidConfigId("bad-"));
    }

    @Test
    @DisplayName("Verify lint ID pattern validation")
    void testLintId() {
        assertTrue(PatternUtil.isValidLintId("my_mob_01"));
        assertTrue(PatternUtil.isValidLintId("item.definition-1"));

        assertFalse(PatternUtil.isValidLintId(null));
        assertFalse(PatternUtil.isValidLintId(""));
        assertFalse(PatternUtil.isValidLintId("invalid:colon"));
        assertFalse(PatternUtil.isValidLintId("invalid uppercase"));
    }

    @Test
    @DisplayName("Verify inline targeter regex parsing")
    void testInlineTargeter() {
        Matcher m1 = PatternUtil.INLINE_TARGETER.matcher("@PlayersInRadius{r=20;limit=3}");
        assertTrue(m1.matches());
        assertEquals("PlayersInRadius", m1.group(1));
        assertEquals("r=20;limit=3", m1.group(2));

        Matcher m2 = PatternUtil.INLINE_TARGETER.matcher("Self");
        assertTrue(m2.matches());
        assertEquals("Self", m2.group(1));
        assertNull(m2.group(2));
    }

    @Test
    @DisplayName("Verify item skill attribute regexes")
    void testItemSkillPatterns() {
        String line = "s=celestial_summon cd=120 ~onDamage";

        Matcher mSkill = PatternUtil.ITEM_SKILL_ID.matcher(line);
        assertTrue(mSkill.find());
        assertEquals("celestial_summon", mSkill.group(1));

        Matcher mCd = PatternUtil.ITEM_SKILL_COOLDOWN.matcher(line);
        assertTrue(mCd.find());
        assertEquals("120", mCd.group(1));

        Matcher mTrigger = PatternUtil.ITEM_SKILL_TRIGGER.matcher(line);
        assertTrue(mTrigger.find());
        assertEquals("onDamage", mTrigger.group(1));
    }

    @Test
    @DisplayName("Verify delimiter and split helper methods")
    void testSplitting() {
        assertArrayEquals(new String[]{"a", "b", "c"}, PatternUtil.splitWhitespace("  a   b  c  "));
        assertArrayEquals(new String[0], PatternUtil.splitWhitespace(null));
        assertArrayEquals(new String[0], PatternUtil.splitWhitespace("   "));

        assertArrayEquals(new String[]{"foo", "bar", "baz"}, PatternUtil.splitCommaOrSemicolon("foo;bar,baz"));
        assertArrayEquals(new String[0], PatternUtil.splitCommaOrSemicolon(null));
        assertArrayEquals(new String[0], PatternUtil.splitCommaOrSemicolon(""));
    }
}

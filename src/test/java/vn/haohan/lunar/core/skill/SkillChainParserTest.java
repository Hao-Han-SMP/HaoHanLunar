package vn.haohan.lunar.core.skill;

import vn.haohan.lunar.api.system.combat.skill.*;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillChainParserTest {

    @Test
    void parsesTypedSkillChainAndMechanicSchedulingFields() throws Exception {
        Path file = Files.createTempFile("skill-", ".yml");
        Files.writeString(file, """
                id: test_slam
                trigger: [onCombat, onSignal]
                cooldown: 20
                targeter: players_in_radius
                conditions:
                  - id: phase
                    value: enraged
                mechanics:
                  - type: damage
                    amount: 10.5
                    delay: 3
                    repeat: 2
                  - type: cast
                    cast-skill: follow_up
                """);

        SkillChainParser.ParseResult result = new SkillChainParser().parse(file);
        SkillChainDefinition chain = result.definitions().get("test_slam");

        assertTrue(result.isValid(), result.errors()::toString);
        assertEquals(2, chain.definition().triggers().size());
        assertEquals(3, chain.mechanics().getFirst().delayTicks());
        assertEquals(2, chain.mechanics().getFirst().repeat());
        assertEquals("follow_up", chain.mechanics().get(1).castSkill());
    }

    @Test
    void reportsSkillAndMechanicFieldForInvalidYaml() throws Exception {
        Path file = Files.createTempFile("skill-", ".yml");
        Files.writeString(file, """
                id: broken
                trigger: [onCombat]
                mechanics:
                  - type: damage
                    delay: invalid
                """);

        SkillChainParser.ParseResult result = new SkillChainParser().parse(file);

        assertFalse(result.isValid());
        assertTrue(result.errors().getFirst().contains("[broken]"));
        assertTrue(result.errors().getFirst().contains("mechanics[0].delay"));
    }
}

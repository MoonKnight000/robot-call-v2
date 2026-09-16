package uz.murodjon.robotcallv2.callqueue.domain.entity;

import java.util.Set;

/**
 * An operator in a queue.
 *
 * @param priority lower rings first; members of equal priority are ordered by the strategy
 * @param skills   tags this operator covers — call languages ("uz-UZ", "ru-RU") for SKILL_BASED
 */
public record CallQueueMember(
        long userId,
        int priority,
        Set<String> skills
) {
    public CallQueueMember {
        skills = skills == null ? Set.of() : Set.copyOf(skills);
    }

    /** Whether this member covers {@code skill}, ignoring case. */
    public boolean hasSkill(String skill) {
        return skill != null && skills.stream().anyMatch(s -> s.equalsIgnoreCase(skill));
    }
}

package me.amjadsmarzug.packobfscurer.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Cleans a Blockbench-style model file.
 *
 * Cube names ("elements[].name") and group names are display labels that the game never reads, so
 * they can be replaced freely. The "groups" tree is editor-only too: the game reads each cube's own
 * rotation and origin, not the groups, so dropping it does not change how the model renders.
 */
final class ModelCleaner {

    /** The cleaned model, plus how much was changed. */
    record Clean(Object doc, int cubesRenamed, int groupsRemoved) {
    }

    private ModelCleaner() {
    }

    static Clean clean(Map<?, ?> model, Supplier<String> newName, boolean renameCubes, boolean removeGroups) {
        Map<String, Object> out = new LinkedHashMap<>();
        model.forEach((k, v) -> out.put(String.valueOf(k), v));

        int[] cubes = {0};
        if (renameCubes && out.get("elements") instanceof List<?> elements) {
            out.put("elements", renameCubes(elements, newName, cubes));
        }

        int groupsRemoved = 0;
        if (removeGroups) {
            if (out.remove("groups") instanceof List<?> groups) {
                groupsRemoved = countGroups(groups);
            }
        } else if (renameCubes && out.get("groups") instanceof List<?> groups) {
            out.put("groups", renameGroups(groups, newName));
        }
        return new Clean(out, cubes[0], groupsRemoved);
    }

    private static List<Object> renameCubes(List<?> elements, Supplier<String> newName, int[] count) {
        List<Object> out = new ArrayList<>(elements.size());
        for (Object el : elements) {
            if (el instanceof Map<?, ?> cube && cube.get("name") instanceof String) {
                Map<String, Object> copy = copyOf(cube);
                copy.put("name", newName.get());
                count[0]++;
                out.add(copy);
            } else {
                out.add(el);
            }
        }
        return out;
    }

    /** Groups nest: each group has a name and a children list that can hold more groups. */
    private static List<Object> renameGroups(List<?> groups, Supplier<String> newName) {
        List<Object> out = new ArrayList<>(groups.size());
        for (Object g : groups) {
            if (!(g instanceof Map<?, ?> group)) {
                out.add(g);
                continue;
            }
            Map<String, Object> copy = copyOf(group);
            if (copy.get("name") instanceof String) {
                copy.put("name", newName.get());
            }
            if (copy.get("children") instanceof List<?> children) {
                copy.put("children", renameGroups(children, newName));
            }
            out.add(copy);
        }
        return out;
    }

    private static int countGroups(List<?> groups) {
        int n = 0;
        for (Object g : groups) {
            if (g instanceof Map<?, ?> group) {
                n++;
                if (group.get("children") instanceof List<?> children) {
                    n += countGroups(children);
                }
            }
        }
        return n;
    }

    private static Map<String, Object> copyOf(Map<?, ?> m) {
        Map<String, Object> copy = new LinkedHashMap<>();
        m.forEach((k, v) -> copy.put(String.valueOf(k), v));
        return copy;
    }
}

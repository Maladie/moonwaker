package com.limelight;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Every translatable default resource has a German counterpart with the same format arguments. */
public class GermanLocalizationTest {
    private static final Pattern ELEMENT = Pattern.compile(
            "<(string|plurals|string-array)\\s+name=\"([^\"]+)\"([^>]*?)(?:/>|>(.*?)</\\1>)",
            Pattern.DOTALL);
    private static final Pattern FORMAT = Pattern.compile(
            "%(?:\\d+\\$)?[-+#0 ,(]*\\d*(?:\\.\\d+)?[sdfxXn%]");
    private static final Pattern ITEM_NOT_REFERENCE = Pattern.compile(
            "<item>(?!@string/)");

    private static Map<String, String> load(String dir) throws IOException {
        Map<String, String> result = new TreeMap<>();
        try (Stream<Path> files = Files.list(Paths.get("src/main/res", dir))) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".xml"))::iterator) {
                String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                Matcher m = ELEMENT.matcher(text);
                while (m.find()) {
                    boolean skipped = m.group(3).contains("translatable=\"false\"")
                            // Arrays of @string references follow each locale's strings.
                            || (m.group(1).equals("string-array")
                            && !ITEM_NOT_REFERENCE.matcher(m.group(4)).find());
                    if (!skipped) result.put(m.group(2), m.group(0));
                }
            }
        }
        return result;
    }

    private static List<String> formats(String element) {
        List<String> found = new ArrayList<>();
        Matcher m = FORMAT.matcher(element);
        while (m.find()) found.add(m.group());
        Collections.sort(found);
        return found;
    }

    @Test
    public void everyDefaultResourceIsTranslatedWithMatchingFormats() throws IOException {
        Map<String, String> base = load("values");
        Map<String, String> german = load("values-de");
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> entry : base.entrySet()) {
            String translated = german.get(entry.getKey());
            if (translated == null) {
                problems.add("missing: " + entry.getKey());
            } else if (!formats(entry.getValue()).equals(formats(translated))) {
                problems.add("format arguments differ: " + entry.getKey());
            }
        }
        assertTrue(problems.toString(), problems.isEmpty());
    }
}

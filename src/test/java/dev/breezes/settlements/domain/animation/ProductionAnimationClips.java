package dev.breezes.settlements.domain.animation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Discovers the production clips, so a clip a factory adds is covered without an edit to any test.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProductionAnimationClips {

    private static final List<AnimationRig> PRODUCTION_RIGS = List.of(
            VillagerAnimationRig.RIG,
            UmbrellaAnimationTargets.RIG,
            BallistaAnimationTargets.RIG);

    /**
     * Returns the clips of every public, static, no-argument KeyframeAnimation factory declared in the production
     * animation package itself.
     */
    static List<KeyframeAnimation> all() throws Exception {
        List<KeyframeAnimation> clips = new ArrayList<>();
        for (Class<?> type : animationPackageClasses()) {
            for (Method method : type.getDeclaredMethods()) {
                int modifiers = method.getModifiers();
                if (Modifier.isPublic(modifiers)
                        && Modifier.isStatic(modifiers)
                        && method.getParameterCount() == 0
                        && method.getReturnType() == KeyframeAnimation.class) {
                    clips.add((KeyframeAnimation) method.invoke(null));
                }
            }
        }
        return clips;
    }

    /**
     * Returns the production rig holding every channel the clip keys, failing when no production rig holds them all.
     */
    static AnimationRig rigFor(KeyframeAnimation clip) {
        for (AnimationRig rig : PRODUCTION_RIGS) {
            if (clip.getTracks().stream().allMatch(track -> rig.contains(track.getTarget()))) {
                return rig;
            }
        }
        return fail(clip.getId() + " keys channels no single production rig holds");
    }

    private static List<Class<?>> animationPackageClasses()
            throws IOException, URISyntaxException, ClassNotFoundException {
        CodeSource codeSource = KeyframeAnimation.class.getProtectionDomain().getCodeSource();
        assertNotNull(codeSource, "No code source to discover production clips from");
        Path root = Path.of(codeSource.getLocation().toURI());
        String packageName = KeyframeAnimation.class.getPackageName();
        String packagePath = packageName.replace('.', '/');

        List<String> classFiles;
        if (Files.isDirectory(root)) {
            classFiles = classFilesIn(root.resolve(packagePath));
        } else {
            try (FileSystem jar = FileSystems.newFileSystem(root)) {
                classFiles = classFilesIn(jar.getPath(packagePath));
            }
        }
        assertTrue(!classFiles.isEmpty(), "No classes found under " + root + " for " + packageName);

        List<Class<?>> classes = new ArrayList<>();
        for (String classFile : classFiles) {
            String className = packageName + "." + classFile.substring(0, classFile.length() - ".class".length());
            classes.add(Class.forName(className, false, KeyframeAnimation.class.getClassLoader()));
        }
        return classes;
    }

    private static List<String> classFilesIn(Path packageDirectory) throws IOException {
        if (!Files.isDirectory(packageDirectory)) {
            fail("Package directory " + packageDirectory + " does not exist");
        }
        try (Stream<Path> entries = Files.list(packageDirectory)) {
            return entries.map(entry -> entry.getFileName().toString())
                    .filter(name -> name.endsWith(".class"))
                    .sorted()
                    .toList();
        }
    }

}

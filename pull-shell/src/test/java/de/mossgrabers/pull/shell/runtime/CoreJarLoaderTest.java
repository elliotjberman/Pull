// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import com.bitwig.fixture.ForbiddenBitwigDependency;

import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreDescriptor;
import de.mossgrabers.pull.core.api.CoreProvider;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.event.ButtonInputEvent;
import de.mossgrabers.pull.core.fixture.CoreRuntimeProvider;
import de.mossgrabers.pull.shell.fixture.ForbiddenShellDependency;

import fixture.core.BitwigAccessProvider;
import fixture.core.ChildDependency;
import fixture.core.FixtureControllerCore;
import fixture.core.SecondProvider;
import fixture.core.ShellAccessProvider;
import fixture.core.SingleProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import static java.nio.file.Files.newOutputStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline isolation tests for candidate core JAR loading.
 */
class CoreJarLoaderTest
{
    private static final String PROVIDER_RESOURCE = "META-INF/services/" + CoreProvider.class.getName ();

    @TempDir
    Path temporaryDirectory;


    @Test
    void loadsChildClassesAgainstTheParentApiAndScopesEveryInvocation () throws Exception
    {
        final Path coreJar = this.createJar ("single.jar", List.of (SingleProvider.class, ChildDependency.class, FixtureControllerCore.class, CoreProvider.class, CoreDescriptor.class), List.of (SingleProvider.class.getName ()));
        final Thread thread = Thread.currentThread ();
        final ClassLoader originalContextClassLoader = thread.getContextClassLoader ();
        final ClassLoader markerClassLoader = new ClassLoader (originalContextClassLoader)
        {
            // Distinct context marker
        };
        LoadedCoreProvider handle = null;
        try
        {
            thread.setContextClassLoader (markerClassLoader);
            handle = new CoreJarLoader ().load (coreJar);
            final LoadedCoreProvider loadedHandle = handle;
            assertSame (markerClassLoader, thread.getContextClassLoader ());

            final CoreProvider provider = loadedHandle.instantiateProvider ();
            assertSame (markerClassLoader, thread.getContextClassLoader ());
            assertSame (provider, loadedHandle.instantiateProvider ());
            assertEquals ("fixture-child", loadedHandle.invokeWithContext (provider::descriptor).buildId ());
            assertInstanceOf (CoreProvider.class, provider);
            assertNotSame (SingleProvider.class, provider.getClass ());

            final ClassLoader candidateClassLoader = provider.getClass ().getClassLoader ();
            assertSame (candidateClassLoader, candidateClassLoader.loadClass (ChildDependency.class.getName ()).getClassLoader ());
            assertSame (CoreProvider.class, candidateClassLoader.loadClass (CoreProvider.class.getName ()));
            assertSame (CoreDescriptor.class, candidateClassLoader.loadClass (CoreDescriptor.class.getName ()));
            assertSame (String.class, candidateClassLoader.loadClass (String.class.getName ()));
            assertSame (candidateClassLoader, loadedHandle.invokeWithContext (() -> Thread.currentThread ().getContextClassLoader ()));
            assertSame (markerClassLoader, thread.getContextClassLoader ());
            assertThrows (IllegalStateException.class, provider::descriptor);
            assertSame (markerClassLoader, thread.getContextClassLoader ());
            final CoreInvocationException invocationFailure = assertThrows (CoreInvocationException.class, () -> loadedHandle.invokeWithContext (() ->
            {
                throw new IllegalArgumentException ("fixture failure");
            }));
            assertNull (invocationFailure.getCause ());
            assertSame (markerClassLoader, thread.getContextClassLoader ());

            loadedHandle.close ();
            loadedHandle.close ();
            assertThrows (IllegalStateException.class, loadedHandle::instantiateProvider);
            assertThrows (IllegalStateException.class, () -> loadedHandle.invokeWithContext (() -> null));
        }
        finally
        {
            if (handle != null)
                handle.close ();
            thread.setContextClassLoader (originalContextClassLoader);
        }
    }


    @Test
    void requiresExactlyOneCandidateProvider () throws Exception
    {
        final Path emptyJar = this.createJar ("empty.jar", List.of (), List.of ());
        final CoreLoadException missing = assertThrows (CoreLoadException.class, () -> new CoreJarLoader ().load (emptyJar));
        assertTrue (missing.getMessage ().contains ("found 0"));

        final Path ambiguousJar = this.createJar ("ambiguous.jar", List.of (SingleProvider.class, SecondProvider.class), List.of (SingleProvider.class.getName (), SecondProvider.class.getName ()));
        final CoreLoadException ambiguous = assertThrows (CoreLoadException.class, () -> new CoreJarLoader ().load (ambiguousJar));
        assertTrue (ambiguous.getMessage ().contains ("found 2"));
    }


    @Test
    void ignoresProviderServicesOnTheParentClasspath () throws Exception
    {
        final Path parentJar = this.createJar ("parent.jar", List.of (SingleProvider.class, ChildDependency.class, FixtureControllerCore.class), List.of (SingleProvider.class.getName ()));
        final Path candidateJar = this.createJar ("candidate.jar", List.of (), List.of ());

        try (URLClassLoader parent = new URLClassLoader (new URL []
        {
            parentJar.toUri ().toURL ()
        }, CoreProvider.class.getClassLoader ()))
        {
            final CoreLoadException failure = assertThrows (CoreLoadException.class, () -> new CoreJarLoader (parent).load (candidateJar));
            assertTrue (failure.getMessage ().contains ("found 0"));
        }
    }


    @Test
    void neverFallsBackToParentImplementationOrDependencyClasses () throws Exception
    {
        this.assertMissingCandidateDependency (SingleProvider.class, "missing-library-dependency.jar");
        this.assertMissingCandidateDependency (CoreRuntimeProvider.class, "missing-core-dependency.jar");
    }


    @Test
    void neverFallsBackToOrdinaryParentResources () throws Exception
    {
        final String parentResource = "fixture/parent-only.txt";
        final Path parentJar = this.createResourceJar ("parent-resource.jar", parentResource, "parent");
        final Path candidateJar = this.createJar ("resource-candidate.jar", List.of (), List.of ());

        try (URLClassLoader parent = new URLClassLoader (new URL []
        {
            parentJar.toUri ().toURL ()
        }, CoreProvider.class.getClassLoader ()); IsolatedCoreClassLoader candidate = new IsolatedCoreClassLoader (candidateJar.toUri ().toURL (), parent))
        {
            assertNull (candidate.getResource (parentResource));
            assertFalse (candidate.getResources (parentResource).hasMoreElements ());

            final String apiResource = CoreProvider.class.getName ().replace ('.', '/') + ".class";
            assertEquals (CoreProvider.class.getClassLoader ().getResource (apiResource), candidate.getResource (apiResource));
        }
    }


    @Test
    void deniesShellAndBitwigClassesEvenWhenPackagedInTheCandidate () throws Exception
    {
        this.assertForbiddenDependency (ShellAccessProvider.class, ForbiddenShellDependency.class, "shell.jar");
        this.assertForbiddenDependency (BitwigAccessProvider.class, ForbiddenBitwigDependency.class, "bitwig.jar");
    }


    @Test
    void loadsStructurallyDifferentVersionsOfTheSameProviderClass () throws Exception
    {
        final Path versionA = this.compileVersionedProvider ("a", false);
        final Path versionB = this.compileVersionedProvider ("b", true);

        try (LoadedCoreProvider handleA = new CoreJarLoader ().load (versionA); LoadedCoreProvider handleB = new CoreJarLoader ().load (versionB))
        {
            final CoreProvider providerA = handleA.instantiateProvider ();
            final CoreProvider providerB = handleB.instantiateProvider ();
            final Class<?> classA = providerA.getClass ();
            final Class<?> classB = providerB.getClass ();

            assertEquals (classA.getName (), classB.getName ());
            assertNotSame (classA, classB);
            assertNotSame (classA.getClassLoader (), classB.getClassLoader ());
            assertEquals ("fixture-a", handleA.invokeWithContext (providerA::descriptor).buildId ());
            assertEquals ("fixture-b", handleB.invokeWithContext (providerB::descriptor).buildId ());
            assertThrows (NoSuchFieldException.class, () -> classA.getDeclaredField ("addedHelper"));
            assertThrows (NoSuchMethodException.class, () -> classA.getDeclaredMethod ("addedMethod"));
            assertNotNull (classB.getDeclaredField ("addedHelper"));
            assertNotNull (classB.getDeclaredMethod ("addedMethod"));
            assertThrows (ClassNotFoundException.class, () -> classA.getClassLoader ().loadClass ("reload.fixture.AddedHelper"));
            assertSame (classB.getClassLoader (), classB.getClassLoader ().loadClass ("reload.fixture.AddedHelper").getClassLoader ());
        }
    }


    @Test
    void runtimeManagerKeepsVersionBActiveWhenBrokenVersionCIsRejected () throws Exception
    {
        final Path versionA = this.compileVersionedProvider ("a", false, false);
        final Path versionB = this.compileVersionedProvider ("b", true, false);
        final Path versionC = this.compileVersionedProvider ("c", true, true);
        final List<Long> committedGenerations = new ArrayList<> ();
        final CoreRuntimeEnvironment environment = new CoreRuntimeEnvironment ()
        {
            private long revision;


            @Override
            public ControllerSnapshot snapshot ()
            {
                final long currentRevision = this.revision++;
                return new ControllerSnapshot (currentRevision, currentRevision, ShellCapabilities.empty (), ClipCatalogSnapshot.empty (), Map.of (), Set.of (), Set.of ());
            }


            @Override
            public PreparedCoreResult prepare (final CoreResult result)
            {
                return new PreparedCoreResult ()
                {
                    // The fixture result needs no shell-side resolution.
                };
            }


            @Override
            public void commit (final long generation, final PreparedCoreResult result)
            {
                committedGenerations.add (Long.valueOf (generation));
            }


            @Override
            public void apply (final long generation)
            {
                // No external effects in this fixture.
            }


            @Override
            public void invalidate (final long generation)
            {
                // No scheduled work in this fixture.
            }
        };
        final RuntimeLog log = new RuntimeLog ()
        {
            @Override
            public void info (final String message)
            {
                // Not needed by these assertions.
            }


            @Override
            public void warn (final String message)
            {
                // The rejected C status is asserted directly.
            }
        };
        final RuntimeManager manager = new RuntimeManager (environment, log);
        manager.start ();
        try
        {
            final ActivationResult activatedA = manager.activate ("fixture-a", new CoreJarLoader ().load (versionA), () -> true);
            final ActivationResult activatedB = manager.activate ("fixture-b", new CoreJarLoader ().load (versionB), () -> true);
            final ActivationResult rejectedC = manager.activate ("fixture-c", new CoreJarLoader ().load (versionC), () -> true);

            assertEquals (ActivationResult.State.ACTIVE, activatedA.state ());
            assertEquals (ActivationResult.State.ACTIVE, activatedB.state ());
            assertEquals (ActivationResult.State.FAILED, rejectedC.state ());
            assertEquals ("fixture-b", rejectedC.activeBuildId ());
            assertEquals ("fixture-b", manager.activeBuildId ());
            assertEquals (2, manager.activeGeneration ());
            assertEquals (List.of (1L, 2L), committedGenerations);

            assertTrue (manager.handle (manager.activeGeneration (), new ButtonInputEvent (1, 0, new ControlId ("fixture"), true)));
            assertEquals (List.of (1L, 2L, 2L), committedGenerations);
        }
        finally
        {
            manager.close ();
        }
    }


    private void assertForbiddenDependency (final Class<? extends CoreProvider> provider, final Class<?> forbiddenDependency, final String fileName) throws Exception
    {
        final Path coreJar = this.createJar (fileName, List.of (provider, forbiddenDependency), List.of (provider.getName ()));
        try (LoadedCoreProvider handle = new CoreJarLoader ().load (coreJar))
        {
            final ClassLoader contextClassLoader = Thread.currentThread ().getContextClassLoader ();
            final CoreLoadException failure = assertThrows (CoreLoadException.class, handle::instantiateProvider);
            assertSame (contextClassLoader, Thread.currentThread ().getContextClassLoader ());
            assertNull (failure.getCause ());
        }
    }


    private void assertMissingCandidateDependency (final Class<? extends CoreProvider> provider, final String fileName) throws Exception
    {
        final Path coreJar = this.createJar (fileName, List.of (provider), List.of (provider.getName ()));
        try (LoadedCoreProvider handle = new CoreJarLoader ().load (coreJar))
        {
            final ClassLoader contextClassLoader = Thread.currentThread ().getContextClassLoader ();
            final CoreLoadException failure = assertThrows (CoreLoadException.class, handle::instantiateProvider);
            assertSame (contextClassLoader, Thread.currentThread ().getContextClassLoader ());
            assertNull (failure.getCause ());
        }
    }


    private Path createJar (final String fileName, final List<Class<?>> classes, final List<String> providers) throws IOException
    {
        final Path jarPath = this.temporaryDirectory.resolve (fileName);
        try (JarOutputStream output = new JarOutputStream (newOutputStream (jarPath)))
        {
            final Set<String> writtenClasses = new LinkedHashSet<> ();
            for (final Class<?> type: classes)
                writeClass (output, type, writtenClasses);

            output.putNextEntry (new JarEntry (PROVIDER_RESOURCE));
            output.write (String.join ("\n", providers).getBytes (StandardCharsets.UTF_8));
            output.closeEntry ();
        }
        return jarPath;
    }


    private Path createResourceJar (final String fileName, final String resourceName, final String contents) throws IOException
    {
        final Path jarPath = this.temporaryDirectory.resolve (fileName);
        try (JarOutputStream output = new JarOutputStream (newOutputStream (jarPath)))
        {
            output.putNextEntry (new JarEntry (resourceName));
            output.write (contents.getBytes (StandardCharsets.UTF_8));
            output.closeEntry ();
        }
        return jarPath;
    }


    private Path compileVersionedProvider (final String version, final boolean structurallyDifferent) throws Exception
    {
        return this.compileVersionedProvider (version, structurallyDifferent, false);
    }


    private Path compileVersionedProvider (final String version, final boolean structurallyDifferent, final boolean failStart) throws Exception
    {
        final Path sourceDirectory = this.temporaryDirectory.resolve ("source-" + version);
        final Path classesDirectory = this.temporaryDirectory.resolve ("classes-" + version);
        final Path packageDirectory = sourceDirectory.resolve ("reload/fixture");
        Files.createDirectories (packageDirectory);
        Files.createDirectories (classesDirectory);

        final String addedField = structurallyDifferent ? "private final AddedHelper addedHelper = new AddedHelper ();" : "";
        final String addedMethod = structurallyDifferent ? "public String addedMethod () { return this.addedHelper.value (); }" : "";
        final String providerSource = """
            package reload.fixture;

            import de.mossgrabers.pull.core.api.ControllerCore;
            import de.mossgrabers.pull.core.api.ControllerSnapshot;
            import de.mossgrabers.pull.core.api.CoreApi;
            import de.mossgrabers.pull.core.api.CoreDescriptor;
            import de.mossgrabers.pull.core.api.CoreProvider;
            import de.mossgrabers.pull.core.api.CoreResult;
            import de.mossgrabers.pull.core.api.ShellCapabilities;
            import de.mossgrabers.pull.core.api.StateEnvelope;
            import de.mossgrabers.pull.core.api.event.CoreEvent;

            import java.util.Optional;

            public final class VersionedProvider implements CoreProvider
            {
                %s

                public CoreDescriptor descriptor ()
                {
                    requireCandidateContext ();
                    return new CoreDescriptor (CoreApi.VERSION, "fixture-%s", "fixture", 1, ShellCapabilities.empty ());
                }

                public ControllerCore create ()
                {
                    requireCandidateContext ();
                    return new VersionedCore ();
                }

                %s

                private static void requireCandidateContext ()
                {
                    if (Thread.currentThread ().getContextClassLoader () != VersionedProvider.class.getClassLoader ())
                        throw new IllegalStateException ("Fixture invoked outside candidate context");
                }

                private static final class VersionedCore implements ControllerCore
                {
                    public CoreResult start (final ControllerSnapshot snapshot, final Optional<StateEnvelope> previousState)
                    {
                        requireCandidateContext ();
                        if (%s)
                            throw new IllegalStateException ("broken fixture start");
                        return CoreResult.empty ();
                    }

                    public CoreResult handle (final CoreEvent event, final ControllerSnapshot snapshot)
                    {
                        requireCandidateContext ();
                        return CoreResult.empty ();
                    }

                    public StateEnvelope checkpoint ()
                    {
                        requireCandidateContext ();
                        return new StateEnvelope ("fixture", 1, new byte [0]);
                    }

                }
            }
            """.formatted (addedField, version, addedMethod, Boolean.toString (failStart));
        final Path providerFile = packageDirectory.resolve ("VersionedProvider.java");
        Files.writeString (providerFile, providerSource, StandardCharsets.UTF_8);

        final List<String> sourceFiles;
        if (structurallyDifferent)
        {
            final Path helperFile = packageDirectory.resolve ("AddedHelper.java");
            Files.writeString (helperFile, "package reload.fixture; public final class AddedHelper { public String value () { return \"added\"; } }", StandardCharsets.UTF_8);
            sourceFiles = List.of (providerFile.toString (), helperFile.toString ());
        }
        else
            sourceFiles = List.of (providerFile.toString ());

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler ();
        assertNotNull (compiler, "Classloader fixture tests require a JDK");
        final Path apiClasses = Path.of (CoreProvider.class.getProtectionDomain ().getCodeSource ().getLocation ().toURI ());
        final ByteArrayOutputStream compilerOutput = new ByteArrayOutputStream ();
        final List<String> arguments = new ArrayList<> (List.of ("--release", "21", "-classpath", apiClasses.toString (), "-d", classesDirectory.toString ()));
        arguments.addAll (sourceFiles);
        final int exitCode = compiler.run (null, compilerOutput, compilerOutput, arguments.toArray (String []::new));
        assertEquals (0, exitCode, compilerOutput.toString (StandardCharsets.UTF_8));

        final Path jarPath = this.temporaryDirectory.resolve ("version-" + version + ".jar");
        try (JarOutputStream output = new JarOutputStream (newOutputStream (jarPath)); Stream<Path> compiledFiles = Files.walk (classesDirectory))
        {
            for (final Path classFile: compiledFiles.filter (Files::isRegularFile).toList ())
            {
                output.putNextEntry (new JarEntry (classesDirectory.relativize (classFile).toString ().replace ('\\', '/')));
                Files.copy (classFile, output);
                output.closeEntry ();
            }
            output.putNextEntry (new JarEntry (PROVIDER_RESOURCE));
            output.write ("reload.fixture.VersionedProvider".getBytes (StandardCharsets.UTF_8));
            output.closeEntry ();
        }
        return jarPath;
    }


    private static void writeClass (final JarOutputStream output, final Class<?> type, final Set<String> writtenClasses) throws IOException
    {
        final String resourceName = type.getName ().replace ('.', '/') + ".class";
        if (!writtenClasses.add (resourceName))
            return;

        final ClassLoader classLoader = type.getClassLoader ();
        final InputStream resource = classLoader == null ? ClassLoader.getSystemResourceAsStream (resourceName) : classLoader.getResourceAsStream (resourceName);
        if (resource == null)
            throw new IOException ("Missing fixture class resource: " + resourceName);

        try (resource)
        {
            output.putNextEntry (new JarEntry (resourceName));
            resource.transferTo (output);
            output.closeEntry ();
        }
    }
}

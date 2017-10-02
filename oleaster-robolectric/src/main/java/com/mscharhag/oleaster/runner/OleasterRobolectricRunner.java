package com.mscharhag.oleaster.runner;

import android.os.Build;

import com.mscharhag.oleaster.runner.suite.Spec;

import org.junit.runner.notification.RunNotifier;
import org.junit.runners.model.InitializationError;
import org.robolectric.DefaultTestLifecycle;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.TestLifecycle;
import org.robolectric.android.internal.ParallelUniverse;
import org.robolectric.annotation.Config;
import org.robolectric.internal.BuckManifestFactory;
import org.robolectric.internal.DefaultManifestFactory;
import org.robolectric.internal.GradleManifestFactory;
import org.robolectric.internal.ManifestFactory;
import org.robolectric.internal.MavenManifestFactory;
import org.robolectric.internal.ParallelUniverseInterface;
import org.robolectric.internal.RoboOleaster;
import org.robolectric.internal.SdkConfig;
import org.robolectric.internal.SdkEnvironment;
import org.robolectric.internal.bytecode.Sandbox;
import org.robolectric.internal.dependency.CachedDependencyResolver;
import org.robolectric.internal.dependency.DependencyResolver;
import org.robolectric.internal.dependency.LocalDependencyResolver;
import org.robolectric.internal.dependency.PropertiesDependencyResolver;
import org.robolectric.manifest.AndroidManifest;
import org.robolectric.res.Fs;
import org.robolectric.res.FsFile;
import org.robolectric.res.PackageResourceTable;
import org.robolectric.res.ResourceMerger;
import org.robolectric.res.ResourcePath;
import org.robolectric.res.ResourceTableFactory;
import org.robolectric.res.RoutingResourceTable;
import org.robolectric.util.Logger;
import org.robolectric.util.ReflectionHelpers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * OleasterRobolectricRunner is an extension of {@link OleasterRunner} and adds robolectric capability.
 * It is instantiated by {@link RoboOleaster} through robolectric class loader.
 * It contains code related to the test callbacks.
 */
public class OleasterRobolectricRunner extends OleasterRunner {

    private static final Map<AndroidManifest, PackageResourceTable> appResourceTableCache = new HashMap<>();
    private static PackageResourceTable compiletimeSdkResourceTable;
    private final SdkEnvironment sandbox;
    private final Config config;
    private final AndroidManifest androidManifest;
    private transient DependencyResolver dependencyResolver;

    public OleasterRobolectricRunner(
            Class<?> testClass,
            SdkEnvironment sandbox,
            Config config,
            AndroidManifest androidManifest)
            throws
            InitializationError {
        super(testClass);
        this.sandbox = sandbox;

        this.config = config;
        this.androidManifest = androidManifest;
    }

    /**
     * Returns the ResourceProvider for the compile time SDK.
     */
    private static PackageResourceTable getCompiletimeSdkResourceTable() {
        if (compiletimeSdkResourceTable == null) {
            ResourceTableFactory resourceTableFactory = new ResourceTableFactory();
            compiletimeSdkResourceTable = resourceTableFactory.newFrameworkResourceTable(
                    new ResourcePath(android.R.class, null, null));
        }
        return compiletimeSdkResourceTable;
    }

    protected void beforeTest(Sandbox sandbox, Spec spec) throws Throwable {
        SdkEnvironment sdkEnvironment = (SdkEnvironment) sandbox;
        RoboSpec roboSpec = (RoboSpec) spec;

        roboSpec.parallelUniverseInterface = getHooksInterface(sdkEnvironment);
        Class<TestLifecycle> cl = sdkEnvironment.bootstrappedClass(getTestLifecycleClass());
        roboSpec.testLifecycle = ReflectionHelpers.newInstance(cl);

        final Config config = roboSpec.config;
        final AndroidManifest appManifest = roboSpec.getAppManifest();

        roboSpec.parallelUniverseInterface.setSdkConfig((sdkEnvironment).getSdkConfig());
        roboSpec.parallelUniverseInterface.resetStaticState(config);

        SdkConfig sdkConfig = roboSpec.sdkConfig;
        Class<?> androidBuildVersionClass = (sdkEnvironment).bootstrappedClass(Build.VERSION.class);
        ReflectionHelpers.setStaticField(androidBuildVersionClass, "SDK_INT", sdkConfig.getApiLevel());
        ReflectionHelpers.setStaticField(androidBuildVersionClass, "RELEASE", sdkConfig.getAndroidVersion());
        ReflectionHelpers.setStaticField(androidBuildVersionClass, "CODENAME", sdkConfig.getAndroidCodeName());

        PackageResourceTable systemResourceTable = sdkEnvironment.getSystemResourceTable(getJarResolver());
        PackageResourceTable appResourceTable = getAppResourceTable(appManifest);

        // This will always be non empty since every class has basic methods like toString.
        Method randomMethod = getTestClass().getJavaClass().getMethods()[0];
        roboSpec.parallelUniverseInterface.setUpApplicationState(
                randomMethod,
                roboSpec.testLifecycle,
                appManifest,
                config,
                new
                        RoutingResourceTable(getCompiletimeSdkResourceTable(), appResourceTable),
                new RoutingResourceTable(systemResourceTable, appResourceTable),
                new RoutingResourceTable(systemResourceTable));
        roboSpec.testLifecycle.beforeTest(null);
    }

    /**
     * Detects which build system is in use and returns the appropriate ManifestFactory implementation.
     *
     * Custom TestRunner subclasses may wish to override this method to provide alternate configuration.
     *
     * @param config Specification of the SDK version, manifest file, package name, etc.
     */
    protected ManifestFactory getManifestFactory(Config config) {
        Properties buildSystemApiProperties = getBuildSystemApiProperties();
        if (buildSystemApiProperties != null) {
            return new DefaultManifestFactory(buildSystemApiProperties);
        }

        Class<?> buildConstants = config.constants();
        //noinspection ConstantConditions
        if (BuckManifestFactory.isBuck()) {
            return new BuckManifestFactory();
        } else if (buildConstants != Void.class) {
            return new GradleManifestFactory();
        } else {
            return new MavenManifestFactory();
        }
    }

    Properties getBuildSystemApiProperties() {
        InputStream resourceAsStream = getClass().getResourceAsStream("/com/android/tools/test_config.properties");
        if (resourceAsStream == null) {
            return null;
        }

        try {
            Properties properties = new Properties();
            properties.load(resourceAsStream);
            return properties;
        } catch (IOException e) {
            return null;
        }
    }

    protected DependencyResolver getJarResolver() {
        if (dependencyResolver == null) {
            if (Boolean.getBoolean("robolectric.offline")) {
                String dependencyDir = System.getProperty("robolectric.dependency.dir", ".");
                dependencyResolver = new LocalDependencyResolver(new File(dependencyDir));
            } else {
                File cacheDir = new File(new File(System.getProperty("java.io.tmpdir")), "robolectric");

                Class<?> mavenDependencyResolverClass = ReflectionHelpers.loadClass(
                        RobolectricTestRunner.class.getClassLoader(),
                        "org.robolectric.internal.dependency.MavenDependencyResolver");
                DependencyResolver dependencyResolver = (DependencyResolver) ReflectionHelpers.callConstructor(
                        mavenDependencyResolverClass);
                if (cacheDir.exists() || cacheDir.mkdir()) {
                    Logger.info("Dependency cache location: %s", cacheDir.getAbsolutePath());
                    this.dependencyResolver = new CachedDependencyResolver(dependencyResolver, cacheDir,
                            60 * 60 * 24 * 1000);
                } else {
                    this.dependencyResolver = dependencyResolver;
                }
            }

            URL buildPathPropertiesUrl = getClass().getClassLoader().getResource("robolectric-deps.properties");
            if (buildPathPropertiesUrl != null) {
                Logger.info("Using Robolectric classes from %s", buildPathPropertiesUrl.getPath());

                FsFile propertiesFile = Fs.fileFromPath(buildPathPropertiesUrl.getFile());
                try {
                    dependencyResolver = new PropertiesDependencyResolver(propertiesFile, dependencyResolver);
                } catch (IOException e) {
                    throw new RuntimeException("couldn't read " + buildPathPropertiesUrl, e);
                }
            }
        }

        return dependencyResolver;
    }

    private PackageResourceTable getAppResourceTable(final AndroidManifest appManifest) {
        PackageResourceTable resourceTable = appResourceTableCache.get(appManifest);
        if (resourceTable == null) {
            resourceTable = new ResourceMerger().buildResourceTable(appManifest);

            appResourceTableCache.put(appManifest, resourceTable);
        }
        return resourceTable;
    }

    ParallelUniverseInterface getHooksInterface(Sandbox sandbox) {
        ClassLoader robolectricClassLoader = sandbox.getRobolectricClassLoader();
        try {
            Class<?> clazz = robolectricClassLoader.loadClass(ParallelUniverse.class.getName());
            Class<? extends ParallelUniverseInterface> typedClazz = clazz.asSubclass(ParallelUniverseInterface.class);
            Constructor<? extends ParallelUniverseInterface> constructor = typedClazz.getConstructor();
            return constructor.newInstance();
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    protected void afterTest(Spec spec) {
        RoboSpec roboSpec = (RoboSpec) spec;

        try {
            roboSpec.parallelUniverseInterface.tearDownApplication();
        } finally {
            try {
//                internalAfterTest(method, bootstrappedMethod);
            } finally {
                Config config = ((RoboSpec) spec).config;
                roboSpec.parallelUniverseInterface.resetStaticState(
                        config); // afterward too, so stuff doesn't hold on to classes?
            }
        }
    }

    protected void finallyAfterTest(Spec spec) {
        RoboSpec roboMethod = (RoboSpec) spec;

        roboMethod.testLifecycle = null;
        roboMethod.parallelUniverseInterface = null;
    }

    @Override
    public List<Spec> getChildren() {
        List<Spec> roboSpecs = new ArrayList<>();

        for (Spec spec : super.getChildren()) {
            roboSpecs.add(new RoboSpec(spec, androidManifest, sandbox.getSdkConfig(), config));
        }
        return roboSpecs;
    }

    @Override
    public void runChild(Spec spec, RunNotifier notifier) {
        List<Spec> specs = spec.getSuite().getSpecs();

        final ClassLoader priorContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(sandbox.getRobolectricClassLoader());

        boolean suiteHasNoSpecs = specs.isEmpty();
        boolean isFirstSpec = specs.indexOf(spec) == 0;
        boolean isLastSpec = specs.indexOf(spec) == specs.size() - 1;

        try {
            beforeTest(sandbox, spec);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        RoboSpec roboSpec = (RoboSpec) spec;
        Thread orig = roboSpec.parallelUniverseInterface.getMainThread();
        roboSpec.parallelUniverseInterface.setMainThread(Thread.currentThread());

        if (suiteHasNoSpecs || isFirstSpec) {
            runBeforeCallbacks(spec);
        }

        if (spec.getBlock().isPresent()) {
            runBeforeEachCallbacks(spec);

            runLeaf(spec, describeChild(spec), notifier);
            runAfterEachCallbacks(spec);
        } else {
            notifier.fireTestIgnored(describeChild(spec));
        }


        if (suiteHasNoSpecs || isLastSpec) {
            runAfterCallbacks(spec);
        }

        roboSpec.parallelUniverseInterface.setMainThread(orig);
        afterTest(spec);
        finallyAfterTest(spec);
        Thread.currentThread().setContextClassLoader(priorContextClassLoader);
    }

    /**
     * An instance of the returned class will be created for each test invocation.
     *
     * Custom TestRunner subclasses may wish to override this method to provide alternate configuration.
     *
     * @return a class which implements {@link TestLifecycle}. This implementation returns a {@link DefaultTestLifecycle}.
     */
    protected Class<? extends TestLifecycle> getTestLifecycleClass() {
        return DefaultTestLifecycle.class;
    }

    public static class RoboSpec extends Spec {

        final SdkConfig sdkConfig;
        final Config config;
        private final AndroidManifest appManifest;
        TestLifecycle testLifecycle;
        ParallelUniverseInterface parallelUniverseInterface;

        RoboSpec(
                Spec spec,
                AndroidManifest appManifest,
                SdkConfig sdkConfig,
                Config config) {
            super(spec);
            this.appManifest = appManifest;
            this.sdkConfig = sdkConfig;
            this.config = config;
        }

        AndroidManifest getAppManifest() {
            return appManifest;
        }
    }
}

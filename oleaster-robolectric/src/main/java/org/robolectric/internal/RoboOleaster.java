package org.robolectric.internal;

import com.mscharhag.oleaster.runner.OleasterRobolectricRunner;
import com.mscharhag.oleaster.runner.OleasterRunner;
import com.mscharhag.oleaster.runner.suite.Spec;

import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.SdkPicker;
import org.robolectric.android.AndroidInterceptors;
import org.robolectric.annotation.Config;
import org.robolectric.internal.bytecode.ClassHandler;
import org.robolectric.internal.bytecode.InstrumentationConfiguration;
import org.robolectric.internal.bytecode.Interceptor;
import org.robolectric.internal.bytecode.Interceptors;
import org.robolectric.internal.bytecode.Sandbox;
import org.robolectric.internal.bytecode.SandboxClassLoader;
import org.robolectric.internal.bytecode.SandboxConfig;
import org.robolectric.internal.bytecode.ShadowMap;
import org.robolectric.internal.bytecode.ShadowWrangler;
import org.robolectric.internal.dependency.CachedDependencyResolver;
import org.robolectric.internal.dependency.DependencyResolver;
import org.robolectric.internal.dependency.LocalDependencyResolver;
import org.robolectric.internal.dependency.PropertiesDependencyResolver;
import org.robolectric.manifest.AndroidManifest;
import org.robolectric.res.Fs;
import org.robolectric.res.FsFile;
import org.robolectric.util.Logger;
import org.robolectric.util.ReflectionHelpers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static java.util.Arrays.asList;
import static org.robolectric.util.ReflectionHelpers.defaultsFor;

/**
 * RobOleaster is the default test runner for running android unit tests using {@link OleasterRunner}.
 * The code is a modified version of {@link RobolectricTestRunner} and {@link SandboxTestRunner}.
 * This class contains the code related to custom classloader and bytecode manipulation.
 * The callbacks related code is delegated to {@link OleasterRobolectricRunner}.
 */
@SuppressWarnings("unchecked")
public class RoboOleaster extends ParentRunner {

    private static final Map<ManifestIdentifier, AndroidManifest> appManifestsCache = new HashMap<>();

    private final Interceptors interceptors;
    private final Object oleasterRobolectricRunner;

    private transient DependencyResolver dependencyResolver;
    static {
        new SecureRandom(); // this starts up the Poller SunPKCS11-Darwin thread early, outside of any Robolectric classloader
    }

    public RoboOleaster(Class testClass) throws InitializationError {
        super(testClass);
        Config config = getConfig(testClass);
        AndroidManifest androidManifest = getAppManifest(config);
        interceptors = new Interceptors(findInterceptors());
        SdkEnvironment sandbox = getSandbox(config, androidManifest);

        // Configure shadows *BEFORE* setting the ClassLoader. This is necessary because
        // creating the ShadowMap loads all ShadowProviders via ServiceLoader and this is
        // not available once we install the Robolectric class loader.
        configureShadows(sandbox);

        Class bootstrappedTestClass = sandbox.bootstrappedClass(testClass);
        try {

            this.oleasterRobolectricRunner = sandbox
                    .bootstrappedClass(OleasterRobolectricRunner.class)
                    .getConstructor(Class.class, SdkEnvironment.class, Config.class, AndroidManifest.class)
                    .newInstance(bootstrappedTestClass, sandbox, config, androidManifest);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Properties getBuildSystemApiProperties() {
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

    private Collection<Interceptor> findInterceptors() {
        return AndroidInterceptors.all();
    }

    private Interceptors getInterceptors() {
        return interceptors;
    }

    private void configureShadows(Sandbox sandbox) {
        ShadowMap.Builder builder = createShadowMap().newBuilder();

        // Configure shadows *BEFORE* setting the ClassLoader. This is necessary because
        // creating the ShadowMap loads all ShadowProvider s via ServiceLoader and this is
        // not available once we install the Robolectric class loader.


        ShadowMap shadowMap = builder.build();
        sandbox.replaceShadowMap(shadowMap);

        sandbox.configure(createClassHandler(shadowMap, sandbox), getInterceptors());
    }

    private ShadowMap createShadowMap() {
        return ShadowMap.EMPTY;
    }

    private ClassHandler createClassHandler(ShadowMap shadowMap, Sandbox sandbox) {
        return new ShadowWrangler(shadowMap, 0, interceptors);
    }

    @Override
    protected List getChildren() {
        try {
            Method method = ParentRunner.class.getDeclaredMethod("getChildren");
            method.setAccessible(true);
            return (List) method.invoke(oleasterRobolectricRunner);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return new ArrayList();
    }

    @Override
    protected Description describeChild(Object object) {
        try {
            Method method = ParentRunner.class.getDeclaredMethod("describeChild", Object.class);
            method.setAccessible(true);
            return (Description) method.invoke(oleasterRobolectricRunner, object);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return Description.EMPTY;
    }

    @Override
    protected void runChild(Object object, RunNotifier runNotifier) {
        try {
            Method method = ParentRunner.class.getDeclaredMethod("runChild", Object.class, RunNotifier.class);
            method.setAccessible(true);
            method.invoke(oleasterRobolectricRunner, object, runNotifier);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private Class<?>[] getExtraShadows() {
        List<Class<?>> shadowClasses = new ArrayList<>();
        addShadows(shadowClasses, getTestClass().getJavaClass().getAnnotation(SandboxConfig.class));
        return shadowClasses.toArray(new Class[shadowClasses.size()]);
    }

    private void addShadows(List<Class<?>> shadowClasses, SandboxConfig annotation) {
        if (annotation != null) {
            shadowClasses.addAll(asList(annotation.shadows()));
        }
    }

    private SdkEnvironment getSandbox(Config config, AndroidManifest androidManifest) {
        InstrumentationConfiguration instrumentationConfiguration = createClassLoaderConfig(config);
        URLClassLoader systemClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
        ClassLoader sandboxClassLoader = new SandboxClassLoader(systemClassLoader, instrumentationConfiguration);
        Sandbox sandbox = new Sandbox(sandboxClassLoader);
        configureShadows(sandbox);

        return SandboxFactory.INSTANCE.getSdkEnvironment(
                instrumentationConfiguration, getJarResolver(), new SdkConfig(pickSdkVersion(config, androidManifest
                )));
    }

    private AndroidManifest getAppManifest(Config config) {
        ManifestFactory manifestFactory = getManifestFactory(config);
        ManifestIdentifier identifier = manifestFactory.identify(config);

        synchronized (appManifestsCache) {
            AndroidManifest appManifest;
            appManifest = appManifestsCache.get(identifier);
            if (appManifest == null) {
                appManifest = manifestFactory.create(identifier);
                appManifestsCache.put(identifier, appManifest);
            }

            return appManifest;
        }
    }

    /**
     * Detects which build system is in use and returns the appropriate ManifestFactory implementation.
     *
     * Custom TestRunner subclasses may wish to override this method to provide alternate configuration.
     *
     * @param config Specification of the SDK version, manifest file, package name, etc.
     */
    private ManifestFactory getManifestFactory(Config config) {
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

    private int pickSdkVersion(Config config, AndroidManifest manifest) {
        if (config != null && config.sdk().length > 1) {
            throw new IllegalArgumentException("Robospock does not support multiple values for @Config.sdk");
        } else if (config != null && config.sdk().length == 1) {
            return config.sdk()[0];
        } else if (manifest != null) {
            return manifest.getTargetSdkVersion();
        } else {
            return SdkConfig.FALLBACK_SDK_VERSION;
        }
    }

    /**
     * NOTE: originally in RobolectricTestRunner getConfig takes Method as parameter
     * and is a bit more complicated
     */
    private Config getConfig(Class<?> clazz) {
        Config classConfig = clazz.getAnnotation(Config.class);
        if (classConfig != null) {
            return classConfig;
        } else {
            return new Config.Builder().build();
        }
    }

    private DependencyResolver getJarResolver() {
        if (dependencyResolver == null) {
            if (Boolean.getBoolean("robolectric.offline")) {
                String dependencyDir = System.getProperty("robolectric.dependency.dir", ".");
                dependencyResolver = new LocalDependencyResolver(new File(dependencyDir));
            } else {
                File cacheDir = new File(new File(System.getProperty("java.io.tmpdir")), "robolectric");

                Class<?> mavenDependencyResolverClass = ReflectionHelpers.loadClass(RobolectricTestRunner.class.getClassLoader(),
                        "org.robolectric.internal.dependency.MavenDependencyResolver");
                DependencyResolver dependencyResolver = (DependencyResolver) ReflectionHelpers.callConstructor(mavenDependencyResolverClass);
                if (cacheDir.exists() || cacheDir.mkdir()) {
                    Logger.info("Dependency cache location: %s", cacheDir.getAbsolutePath());
                    this.dependencyResolver = new CachedDependencyResolver(dependencyResolver, cacheDir, 60 * 60 * 24 * 1000);
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

    /**
     * Create an {@link InstrumentationConfiguration} suitable for the provided {@link FrameworkMethod}.
     *
     * Custom TestRunner subclasses may wish to override this method to provide alternate configuration.
     *
     * @return an {@link InstrumentationConfiguration}
     */
    private InstrumentationConfiguration createClassLoaderConfig(Config config) {
        InstrumentationConfiguration.Builder builder = InstrumentationConfiguration.newBuilder()
                .doNotAcquirePackage("java.")
                .doNotAcquirePackage("sun.")
                .doNotAcquirePackage("org.robolectric.annotation.")
                .doNotAcquirePackage("org.robolectric.internal.")
                .doNotAcquirePackage("org.robolectric.util.")
                .doNotAcquirePackage("org.junit.");

        for (Class<?> shadowClass : getExtraShadows()) {
            ShadowMap.ShadowInfo shadowInfo = ShadowMap.getShadowInfo(shadowClass);
            builder.addInstrumentedClass(shadowInfo.getShadowedClassName());
        }

        addInstrumentedPackages(builder);
        AndroidConfigurer.configure(builder, getInterceptors());
        AndroidConfigurer.withConfig(builder, config);
        return builder.build();
    }

    private void addInstrumentedPackages(InstrumentationConfiguration.Builder builder) {
        SandboxConfig classConfig = getTestClass().getJavaClass().getAnnotation(SandboxConfig.class);
        if (classConfig != null) {
            for (String pkgName : classConfig.instrumentedPackages()) {
                builder.addInstrumentedPackage(pkgName);
            }
        }
    }
}

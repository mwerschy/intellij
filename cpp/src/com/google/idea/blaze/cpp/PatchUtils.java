package com.google.idea.blaze.cpp;

import com.intellij.openapi.application.Application;
import cpp.src.com.google.idea.blaze.cpp.PatchClass;
import org.apache.commons.io.IOUtils;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PatchUtils {
  private static final ClassLoader applicationClassLoader = Application.class.getClassLoader();

  static {
    // Copy our patch class to the application class loader
    try {
      byte[] bytes = IOUtils.toByteArray(getClassStream(PatchClass.class.getClassLoader(), PatchClass.class.getCanonicalName()));
      defineClass(applicationClassLoader, PatchClass.class.getCanonicalName(), bytes);
    } catch (IOException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }

    // Patch relevant application classes
    Arrays.stream(PatchClass.class.getDeclaredMethods())
        .filter(method -> method.isAnnotationPresent(Patch.class))
        .collect(Collectors.groupingBy(method -> method.getAnnotation(Patch.class).value()))
        .forEach((className, methods) -> {
          try {
            InputStream inputStream = getClassStream(applicationClassLoader, className);
            byte[] bytes = PatchMethodVisitor.transform(inputStream, methods);
            defineClass(applicationClassLoader, className, bytes);
          } catch (IOException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static MethodVisitor patch(ClassVisitor cv, Method targetMethod) {
    Patch patch = targetMethod.getAnnotation(Patch.class);
    String descriptor = getPatchMethodDescriptor(targetMethod);
    MethodVisitor mv = cv.visitMethod(patch.access(), targetMethod.getName(), descriptor, null, null);
    GeneratorAdapter adapter = new GeneratorAdapter(mv, patch.access(), targetMethod.getName(), descriptor);

    if ((patch.access() & Opcodes.ACC_STATIC) != Opcodes.ACC_STATIC) adapter.loadThis();
    adapter.loadArgs();
    adapter.invokeStatic(Type.getType(PatchClass.class), org.jetbrains.org.objectweb.asm.commons.Method.getMethod(targetMethod));
    adapter.returnValue();
    adapter.endMethod();

    return adapter;
  }

  private static InputStream getClassStream(ClassLoader classLoader, String className) {
    return classLoader.getResourceAsStream(getClassType(className) + ".class");
  }

  private static String getClassType(String className) {
    return className.replace('.', '/');
  }

  private static void defineClass(ClassLoader classLoader, String className, byte[] bytes) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Method defineClassMethod = ClassLoader.class.getDeclaredMethod(
        "defineClass",
        String.class,
        byte[].class,
        int.class,
        int.class
    );
    defineClassMethod.setAccessible(true);
    defineClassMethod.invoke(classLoader, className, bytes, 0, bytes.length);
  }

  private static String getPatchMethodDescriptor(Method method) {
    Patch patch = method.getAnnotation(Patch.class);
    return Type.getMethodDescriptor(
        Type.getType(method.getReturnType()),
        Arrays.stream(method.getParameterTypes())
            .skip((patch.access() & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC ? 0 : 1)
            .map(Type::getType)
            .toArray(Type[]::new));
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  public @interface Patch {
    String value();

    int access() default Opcodes.ACC_PUBLIC;
  }

  private static class PatchMethodVisitor extends ClassVisitor {
    private final Map<String, Method> methods;

    private PatchMethodVisitor(List<Method> methods, ClassVisitor cv) {
      super(Opcodes.ASM7, cv);
      this.methods = methods.stream().collect(Collectors.toMap(
          method -> method.getName() + getPatchMethodDescriptor(method),
          Function.identity()
      ));
    }

    @Override
    public MethodVisitor visitMethod(
        int access,
        String name,
        String descriptor,
        String signature,
        String[] exceptions
    ) {
      Method method = methods.get(name + descriptor);
      if (method != null) {
        methods.remove(name + descriptor);
        return patch(cv, method);
      }

      return super.visitMethod(access, name, descriptor, signature, exceptions);
    }

    public static byte[] transform(InputStream inputStream, List<Method> methods) throws IOException {
      ClassReader reader = new ClassReader(inputStream);
      ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
      PatchMethodVisitor visitor = new PatchMethodVisitor(methods, writer);
      reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
      if (!visitor.methods.isEmpty()) {
        throw new IllegalStateException("Failed to patch the following methods:\n\t\t" +
            String.join("\n\t\t", visitor.methods.keySet()));
      }
      return writer.toByteArray();
    }
  }
}

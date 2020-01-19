package com.google.idea.blaze.cpp;

import com.intellij.openapi.application.Application;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.commons.GeneratorAdapter;
import org.jetbrains.org.objectweb.asm.commons.Method;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.function.Function;

public class WindowsClangPatcher {
  private static final String CLASS_NAME = "com.jetbrains.cidr.lang.toolchains.CidrSwitchBuilder";
  private static final String METHOD_NAME = "addSingleRaw";
  private static final String METHOD_SIGNATURE = String.format("%s %s(String)", CLASS_NAME, METHOD_NAME);

  private static final ClassLoader applicationClassLoader = Application.class.getClassLoader();

  static {
    try {
      patch();
    } catch (IOException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      e.printStackTrace();
    }
  }

  private static void patch() throws IOException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    InputStream inputStream = getClassStream(CLASS_NAME);
    byte[] bytes = PatchMethodVisitor.transform(inputStream, METHOD_NAME, cv -> {
      Method method = Method.getMethod(METHOD_SIGNATURE);
      Type classType = Type.getType(String.format("L%s;", getClassType(CLASS_NAME)));

      MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC, method.getName(), method.getDescriptor(), null, null);
      GeneratorAdapter adapter = new GeneratorAdapter(mv, Opcodes.ACC_PUBLIC, method.getName(), method.getDescriptor());
      // public CidrSwitchBuilder addSingleRaw(@Nullable String rawArg) {
      adapter.push("-dD");
      adapter.loadArg(0);
      adapter.invokeVirtual(Type.getType(String.class), Method.getMethod("boolean equals(Object)"));
      Label rawArgDumpDebugLbl = adapter.newLabel();
      adapter.ifZCmp(GeneratorAdapter.EQ, rawArgDumpDebugLbl);
      //   if ("-dD".equals(rawArg)) {
      adapter.loadThis();
      adapter.getField(classType, "myArgs", Type.getType(List.class));
      adapter.push("-Xclang");
      adapter.invokeInterface(Type.getType(List.class), Method.getMethod("boolean add(Object)"));
      adapter.pop();
      //     this.myArgs.add("-Xclang");
      adapter.mark(rawArgDumpDebugLbl);
      //   }
      adapter.loadArg(0);
      Label rawArgNotNullLbl = adapter.newLabel();
      adapter.ifNull(rawArgNotNullLbl);
      //   if (rawArg != null) {
      adapter.loadThis();
      adapter.getField(classType, "myArgs", Type.getType(List.class));
      adapter.loadArg(0);
      adapter.invokeInterface(Type.getType(List.class), Method.getMethod("boolean add(Object)"));
      adapter.pop();
      //     this.myArgs.add(rawArg);
      adapter.mark(rawArgNotNullLbl);
      //   }
      adapter.loadThis();
      adapter.returnValue();
      //   return this;
      adapter.endMethod();
      // }

      return adapter;
    });
    defineClass(CLASS_NAME, bytes);
  }

  private static InputStream getClassStream(String className) {
    return applicationClassLoader.getResourceAsStream(getClassType(className) + ".class");
  }

  private static String getClassType(String className) {
    return className.replace('.', '/');
  }

  private static void defineClass(String className, byte[] bytes) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    java.lang.reflect.Method defineClassMethod = ClassLoader.class.getDeclaredMethod(
        "defineClass",
        String.class,
        byte[].class,
        int.class,
        int.class
    );
    defineClassMethod.setAccessible(true);
    defineClassMethod.invoke(applicationClassLoader, className, bytes, 0, bytes.length);
  }

  private static class PatchMethodVisitor extends ClassVisitor {
    private final String methodName;
    private final Function<ClassVisitor, MethodVisitor> patch;

    private PatchMethodVisitor(String methodName, Function<ClassVisitor, MethodVisitor> patch, ClassVisitor cv) {
      super(Opcodes.ASM7, cv);
      this.methodName = methodName;
      this.patch = patch;
    }

    @Override
    public MethodVisitor visitMethod(
        int access,
        String name,
        String descriptor,
        String signature,
        String[] exceptions
    ) {
      if (name.equals(methodName)) return patch.apply(cv);
      return super.visitMethod(access, name, descriptor, signature, exceptions);
    }

    public static byte[] transform(
        InputStream inputStream,
        String methodName,
        Function<ClassVisitor, MethodVisitor> patch
    ) throws IOException {
      ClassReader reader = new ClassReader(inputStream);
      ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
      PatchMethodVisitor visitor = new PatchMethodVisitor(methodName, patch, writer);
      reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
      return writer.toByteArray();
    }
  }
}

package cc.unilock.remapper;

import net.fabricmc.loom.mcp.McpMappings;
import net.fabricmc.loom.mcp.McpMappingsBuilder;
import net.fabricmc.loom.util.StringInterner;
import net.fabricmc.loom.util.ZipUtil;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;

public class Main {
    public static void main(String[] args) {
        File mcpZip = new File("mcp.zip");
        File input = new File("input");
        File output = new File("output");

        if (!mcpZip.exists()) {
            throw new RuntimeException("mcp.zip not found!");
        }
        if (!input.exists()) {
            throw new RuntimeException("input dir not found!");
        }
        if (!output.exists() && !output.mkdirs()) {
            throw new RuntimeException("failed to create output dir!");
        }

        McpMappingsBuilder mcpBuilder = new McpMappingsBuilder();
        StringInterner mem = new StringInterner();
        try (FileSystem mcpZipFs = ZipUtil.openFs(mcpZip.toPath())) {
            mcpBuilder.importEverythingFromZip(mcpZipFs, mem);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

        McpMappings mcp = mcpBuilder.build();

        TinyRemapper tiny = TinyRemapper.newRemapper()
                .ignoreFieldDesc(true) //MCP doesn't have them
                .renameInvalidLocals(true)
                .rebuildSourceFilenames(true)
                .skipLocalVariableMapping(true)
                .withMappings(mcp.chooseSrg("joined").toMappingProvider())
                .extraPostApplyVisitor((trclass, next) -> new Asm4CompatClassVisitor(next)) //TODO maybe move this lol
                .build();

        try (OutputConsumerPath outputConsumer = new OutputConsumerPath.Builder(output.toPath()).build()) {
            outputConsumer.addNonClassFiles(input.toPath());

            tiny.readInputs(input.toPath());
            //tiny.readClassPath(classpath);

            tiny.apply(outputConsumer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            tiny.finish();
        }
    }

    /**
     * Basically tiny-remapper is putting things into the class file that aren't compatible with ASM api level 4, which
     * many versions of Forge use to parse mod classes. Ex., for some reason after remapping, a parameter-name table
     * shows up out of nowhere.
     * <p>
     * There are more things that aren't compatible with asm api 4 but i don't think tiny-remapper will add them,
     * and they aren't as easily silently-droppable as this stuff (like what am i supposed to do if i find an
     * invokedynamic at this stage lmao)
     * <p>
     * <a href="https://www.youtube.com/watch?v=n2IZbbuFxWg">https://www.youtube.com/watch?v=n2IZbbuFxWg</a>
     */
    private static class Asm4CompatClassVisitor extends ClassVisitor {
        public Asm4CompatClassVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM4, classVisitor);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
            return null; //No! I don't want that.
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            return new FieldVisitor(Opcodes.ASM4, super.visitField(access, name, descriptor, signature, value)) {
                @Override
                public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                    return null; //No! I don't want that.
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM4, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                @Override
                public void visitParameter(String name, int access) {
                    //No! I don't want that.
                }

                @Override
                public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                    return null; //No! I don't want that.
                }

                @Override
                public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                    return null; //No! I don't want that.
                }

                @Override
                public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
                    return null; //No! I don't want that.
                }

                @Override
                public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String descriptor, boolean visible) {
                    return null; //No! I don't want that.
                }
            };
        }
    }
}

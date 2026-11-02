import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.AttributedElement;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * Finds code that works in singleplayer but breaks on the other side of a real server. Forge strips
 * {@code @SideOnly(Side.CLIENT)} classes and members from a dedicated server (and {@code Side.SERVER} ones from a client),
 * so a reference to one only fails there, with NoSuchMethodError, NoSuchFieldError or NoClassDefFoundError.
 *
 * <p>Usage: {@code java SideCheck.java <classpath> <classes dirs> <client prefix>...}, both paths separated like a
 * classpath. Classes under a client prefix
 * are checked against server-only members, everything else against client-only ones and the client prefixes
 * themselves. Exits 1 when it finds anything.
 */
public final class SideCheck {

    private static final String SIDE_ONLY = "Lcpw/mods/fml/relauncher/SideOnly;";
    /** Client code that has no {@code @SideOnly} on it but never exists on a dedicated server. */
    private static final List<String> CLIENT_PACKAGES = List.of(
        "net/minecraft/client/",
        "net/minecraftforge/client/",
        "cpw/mods/fml/client/");

    /** Hierarchy and sidedness of one class; members are keyed {@code name + descriptor}. */
    private record Info(String superName, List<String> interfaces, String side, Map<String, String> memberSides) {}

    private final Map<String, Info> index = new HashMap<>();
    private final List<String> clientPrefixes;
    private final Set<String> findings = new TreeSet<>();

    private SideCheck(List<String> clientPrefixes) {
        this.clientPrefixes = clientPrefixes;
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("usage: java SideCheck.java <classpath> <classes dirs> <client prefix>...");
            System.exit(2);
        }
        SideCheck check = new SideCheck(List.of(Arrays.copyOfRange(args, 2, args.length)));
        Map<String, ClassModel> targets = new LinkedHashMap<>();
        for (String entry : args[1].split(java.io.File.pathSeparator)) {
            if (!entry.isEmpty()) {
                readClasses(Path.of(entry)).forEach(targets::putIfAbsent);
            }
        }
        targets.forEach((name, model) -> check.index.put(name, info(model)));
        for (String entry : args[0].split(java.io.File.pathSeparator)) {
            if (!entry.isEmpty()) {
                readClasses(Path.of(entry)).forEach((name, model) -> check.index.putIfAbsent(name, info(model)));
            }
        }
        targets.forEach(check::scan);
        check.findings.forEach(System.out::println);
        System.out.printf("SideCheck: %d classes checked against %d indexed, %d finding(s)%n",
            targets.size(), check.index.size(), check.findings.size());
        System.exit(check.findings.isEmpty() ? 0 : 1);
    }

    private static Map<String, ClassModel> readClasses(Path path) {
        Map<String, ClassModel> classes = new LinkedHashMap<>();
        try {
            if (Files.isDirectory(path)) {
                try (Stream<Path> files = Files.walk(path)) {
                    for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".class"))::iterator) {
                        add(classes, Files.readAllBytes(file));
                    }
                }
            } else if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) {
                try (ZipFile zip = new ZipFile(path.toFile())) {
                    var entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        var entry = entries.nextElement();
                        if (entry.getName().endsWith(".class") && !entry.getName().startsWith("META-INF/")) {
                            add(classes, zip.getInputStream(entry).readAllBytes());
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(path.toString(), e);
        }
        return classes;
    }

    private static void add(Map<String, ClassModel> classes, byte[] bytes) {
        try {
            ClassModel model = ClassFile.of().parse(bytes);
            classes.putIfAbsent(model.thisClass().asInternalName(), model);
        } catch (IllegalArgumentException e) {
            // module-info or a class file this JDK can't read: not something Minecraft code links against
        }
    }

    private static Info info(ClassModel model) {
        Map<String, String> members = new HashMap<>();
        for (MethodModel method : model.methods()) {
            members.put(method.methodName().stringValue() + method.methodType().stringValue(), side(method));
        }
        for (FieldModel field : model.fields()) {
            members.put(field.fieldName().stringValue() + ":" + field.fieldType().stringValue(), side(field));
        }
        return new Info(
            model.superclass().map(ClassEntry::asInternalName).orElse(null),
            model.interfaces().stream().map(ClassEntry::asInternalName).toList(),
            side(model),
            members);
    }

    /** "CLIENT" or "SERVER" from a {@code @SideOnly}, or null. */
    private static String side(AttributedElement element) {
        return element.findAttribute(Attributes.runtimeVisibleAnnotations())
            .flatMap(attribute -> attribute.annotations().stream()
                .filter(annotation -> annotation.className().stringValue().equals(SIDE_ONLY))
                .findFirst())
            .flatMap(SideCheck::sideValue)
            .orElse(null);
    }

    private static java.util.Optional<String> sideValue(Annotation annotation) {
        return annotation.elements().stream()
            .filter(element -> element.name().stringValue().equals("value"))
            .map(element -> element.value() instanceof AnnotationValue.OfEnum value ? value.constantName().stringValue() : null)
            .findFirst();
    }

    private void scan(String name, ClassModel model) {
        boolean clientCode = isClientPrefix(name);
        String wrong = clientCode ? "SERVER" : "CLIENT";
        for (MethodModel method : model.methods()) {
            String where = name + "." + method.methodName().stringValue();
            method.code().ifPresent(code -> {
                for (CodeElement element : code) {
                    switch (element) {
                        case FieldInstruction f -> member(where, f.owner().asInternalName(),
                            f.name().stringValue() + ":" + f.type().stringValue(), wrong, clientCode);
                        case InvokeInstruction i -> member(where, i.owner().asInternalName(),
                            i.name().stringValue() + i.type().stringValue(), wrong, clientCode);
                        case InvokeDynamicInstruction d -> {
                            for (ConstantDesc arg : d.bootstrapArgs()) {
                                if (arg instanceof DirectMethodHandleDesc handle) {
                                    member(where, internal(handle.owner().descriptorString()),
                                        handle.methodName() + handle.lookupDescriptor(), wrong, clientCode);
                                }
                            }
                        }
                        case NewObjectInstruction n -> type(where, n.className().asInternalName(), wrong, clientCode);
                        case TypeCheckInstruction t -> type(where, t.type().asInternalName(), wrong, clientCode);
                        case NewReferenceArrayInstruction a -> type(where, a.componentType().asInternalName(), wrong,
                            clientCode);
                        case ConstantInstruction.LoadConstantInstruction l when l
                            .constantEntry() instanceof ClassEntry c -> type(where, c.asInternalName(), wrong,
                                clientCode);
                        default -> {}
                    }
                }
            });
        }
    }

    private void type(String where, String type, String wrong, boolean clientCode) {
        String name = type.replaceFirst("^\\[+L?", "").replaceFirst(";$", "");
        String reason = classReason(name, wrong, clientCode);
        if (reason != null) {
            findings.add(where + " -> " + name + " (" + reason + ")");
        }
    }

    private void member(String where, String owner, String member, String wrong, boolean clientCode) {
        if (owner.startsWith("[")) {
            return;
        }
        String reason = classReason(owner, wrong, clientCode);
        if (reason != null) {
            findings.add(where + " -> " + owner + "." + member + " (" + reason + ")");
            return;
        }
        String declaring = resolve(owner, member);
        if (declaring != null && wrong.equals(index.get(declaring).memberSides().get(member))) {
            findings.add(where + " -> " + declaring + "." + member + " (@SideOnly(" + wrong + ") member)");
        }
    }

    /** Why loading this class fails on the other side, or null. */
    private String classReason(String name, String wrong, boolean clientCode) {
        if (!clientCode) {
            if (isClientPrefix(name)) {
                return "client-only GTBlocker code";
            }
            if (CLIENT_PACKAGES.stream().anyMatch(name::startsWith)) {
                return "client package";
            }
        }
        Info info = index.get(name);
        return info != null && wrong.equals(info.side()) ? "@SideOnly(" + wrong + ") class" : null;
    }

    /** The class that declares the member: the superclass chain first, then the interfaces, as the JVM resolves it. */
    private String resolve(String owner, String member) {
        List<String> interfaces = new ArrayList<>();
        for (String current = owner; current != null;) {
            Info info = index.get(current);
            if (info == null) {
                break;
            }
            if (info.memberSides().containsKey(member)) {
                return current;
            }
            interfaces.addAll(info.interfaces());
            current = info.superName();
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < interfaces.size(); i++) {
            Info info = index.get(interfaces.get(i));
            if (info == null || !seen.add(interfaces.get(i))) {
                continue;
            }
            if (info.memberSides().containsKey(member)) {
                return interfaces.get(i);
            }
            interfaces.addAll(info.interfaces());
        }
        return null;
    }

    private boolean isClientPrefix(String name) {
        return clientPrefixes.stream().anyMatch(name::startsWith);
    }

    private static String internal(String descriptor) {
        return descriptor.startsWith("L") ? descriptor.substring(1, descriptor.length() - 1) : descriptor;
    }
}

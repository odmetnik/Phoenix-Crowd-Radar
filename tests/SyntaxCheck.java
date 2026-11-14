import com.sun.source.util.JavacTask;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;

/** Parse Android sources without pretending to type-check unavailable Android APIs. */
public class SyntaxCheck {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(diagnostics,null,null)) {
            List<Path> paths;
            try(var files=Files.walk(Path.of("app/src/main/java"))){paths=files.filter(p->p.toString().endsWith(".java")).toList();}
            var inputs=manager.getJavaFileObjectsFromPaths(paths);
            JavacTask task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("-proc:none"),null,inputs);
            task.parse();
            for(var d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR)throw new AssertionError(d.toString());
            System.out.println("PASS: syntax parsed for "+paths.size()+" Java files (not an Android build)");
        }
    }
}

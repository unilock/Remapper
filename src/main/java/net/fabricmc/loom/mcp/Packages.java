package net.fabricmc.loom.mcp;

import net.fabricmc.loom.util.StringInterner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for Forge's packages.csv.
 */
public class Packages {
    private final Map<String, String> packages = new HashMap<>();

    public Packages read(Path path, StringInterner mem) throws IOException {
        List<String> lines = Files.readAllLines(path);
        for(int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if(line.isEmpty()) continue;
            if((i == 0 && "class,package".equals(line))) continue; //the csv header
            int lineNo = i + 1;

            //Example packages.csv line:
            // BlockAnvil,net/minecraft/block

            String[] split = line.split(",", 2);
            if(split.length != 2) {
                System.err.println("line " + lineNo + " has weird number of elements: " + line);
                continue;
            }

            packages.put(mem.intern(split[0]), mem.intern(split[1]));
        }

        return this;
    }

    public void mergeWith(Packages other) {
        packages.putAll(other.packages);
    }

    public boolean isEmpty() {
        return packages.isEmpty();
    }

    /**
     * Applies the packaging transformation to a class name, in internal format.
     */
    public String repackage(String srgClass) {
        //remove the package prefix from the class
        String srgClassNameOnly = srgClass;
        int lastSlash = srgClass.lastIndexOf('/');
        if(lastSlash != -1) srgClassNameOnly = srgClass.substring(lastSlash + 1);

        //the values of the map are the *new* package that the class should go in.
        //if we get a hit, glue the new package onto the old class name
        String lookup = packages.get(srgClassNameOnly);
        if(lookup != null) return lookup + "/" + srgClassNameOnly;

        //If we don't get a hit, we might be repackaging a class like net/minecraft/src/Block$1. This class name
        //was invented by Srg#augment and (since we didn't get a hit above) it doesn't exist in the package mappings.
        //Well, we should try repackaging this class like its equivalent without the $1.
        int dollarIndex = srgClass.indexOf('$');
        if(dollarIndex != -1) {
            String prefix = srgClass.substring(0, dollarIndex); //"net/minecraft/src/Block"
            String suffix = srgClass.substring(dollarIndex); //"$1"
            return repackage(prefix) + suffix; //"net/minecraft/block/Block" + "$1"
        }

        //No packaging transformation exists for this class, and it's also not an inner class. Leave it alone.
        return srgClass;
    }

    /**
     * Calls {@code repackage} on all internal-format class names it can find in the descriptor.
     */
    public String repackageDescriptor(String descriptor) {
        return DescriptorMapper.map(descriptor, this::repackage);
    }
}

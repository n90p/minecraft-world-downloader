package game.data.chunk.palette;

import com.google.gson.Gson;
import com.google.gson.JsonPrimitive;
import org.apache.commons.lang3.NotImplementedException;
import se.llbit.nbt.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Holds a global palette as introduced in 1.13. These are read from a simple JSON file that is generated by the
 * Minecraft server.jar. More details are in the readme file in the resource folder.
 */
public class GlobalPalette implements StateProvider {
    private static final HashMap<String, JsonPrimitive> EMPTY_MAP = new HashMap<>();
    private final Map<Integer, BlockState> states;
    private final Map<BlockStateIdentifier, BlockState> nameStates;
    private String version;

    /**
     * Instantiate a global palette using the given Minecraft version.
     * @param version the Minecraft version (e.g. 1.12.2), NOT protocol version
     */
    public GlobalPalette(String version) {
        this(GlobalPalette.class.getClassLoader().getResourceAsStream("blocks-" + version + ".json"));
    }

    /**
     * Instantiate a global palette using the input stream (to a JSON file).
     */
    public GlobalPalette(InputStream input) {
        this.states = new HashMap<>();
        this.nameStates = new HashMap<>();

        // if the file doesn't exist, there is no palette for this version.
        if (input == null) { return; }

        JsonResult map = new Gson().fromJson(new InputStreamReader(input), JsonResult.class);
        map.forEach((name, type) -> type.states.forEach(state -> {
            if (state.properties == null) {
                state.properties = EMPTY_MAP;
            }

            CompoundTag properties = state.getProperties();

            BlockState s = new BlockState(name, state.id, properties);
            states.put(state.id, s);
            nameStates.put(new BlockStateIdentifier(name, properties), s);
        }));
    }

    public int getRequiredBits() {
        return (int) Math.ceil(Math.log(states.size()) / Math.log(2));
    }

    /**
     * Get a block state from a given index. Used to convert packet palettes to the global palette.
     */
    @Override
    public BlockState getState(int key) {
        return states.getOrDefault(key, null);
    }

    /**
     * Returns the first state in the palette, used to replace unknown states with air.
     */
    @Override
    public BlockState getDefaultState() {
        return states.values().iterator().next();
    }

    @Override
    public BlockState getState(SpecificTag nbt) {
        return nameStates.get(new BlockStateIdentifier(nbt));
    }

    @Override
    public int getStateId(SpecificTag nbt) {
        BlockState state = getState(nbt);
        if (state == null) {
            return 0;
        }
        return state.getNumericId();
    }
}

class BlockStateIdentifier {
    String name;
    CompoundTag properties;

    public BlockStateIdentifier(SpecificTag t) {
        this(t.get("Name").stringValue(), t.get("Properties").asCompound());
    }

    public BlockStateIdentifier(String name, CompoundTag properties) {
        this.name = name;
        this.properties = properties;

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BlockStateIdentifier that = (BlockStateIdentifier) o;

        if (!Objects.equals(name, that.name)) return false;
        return Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (properties != null ? properties.hashCode() : 0);
        return result;
    }
}

// we need a class to represent this type because of type erasure, otherwise Gson will get angry over casting.
class JsonResult extends HashMap<String, JsonBlockType> { }

// additional classes for inside the JsonResult
class JsonBlockType { ArrayList<JsonBlockState> states; }
class JsonBlockState {
    int id;
    HashMap<String, JsonPrimitive> properties;

    /**
     * Turns properties hashmap into a compoundtag, which lets us correctly handle the block states that depend on
     * properties.
     */
    CompoundTag getProperties() {
        CompoundTag res = new CompoundTag();
        for (Map.Entry<String, JsonPrimitive> entry : properties.entrySet()) {
            JsonPrimitive wrappedVal = entry.getValue();
            SpecificTag value;

            if (wrappedVal.isBoolean()) {
                value = new ByteTag(wrappedVal.getAsBoolean() ? 1 : 0);
            } else if (wrappedVal.isNumber()) {
                value = new IntTag(wrappedVal.getAsInt());
            } else {
                value = new StringTag(wrappedVal.getAsString());
            }

            res.add(entry.getKey(), value);

        }
        return res;
    }
}

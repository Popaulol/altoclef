package adris.altoclef.tasks;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.tasks.misc.TimeoutWanderTask;
import adris.altoclef.tasksystem.ITaskRequiresGrounded;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.util.ItemTarget;
import adris.altoclef.util.csharpisbetter.Util;
import adris.altoclef.util.progresscheck.MovementProgressChecker;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.util.math.Vec3d;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PickupDroppedItemTask extends AbstractDoToClosestObjectTask<ItemEntity> implements ITaskRequiresGrounded {

    private final ItemTarget[] _itemTargets;

    private final Set<ItemEntity> _blacklist = new HashSet<>();

    private final MovementProgressChecker _progressChecker = new MovementProgressChecker(3);
    private final TimeoutWanderTask _wanderTask = new TimeoutWanderTask(20);

    private ItemEntity _currentDrop = null;

    private final boolean _freeInventoryIfFull;

    private boolean _fullCheckFailed = false;


    public PickupDroppedItemTask(ItemTarget[] itemTargets, boolean freeInventoryIfFull) {
        _itemTargets = itemTargets;
        _freeInventoryIfFull = freeInventoryIfFull;
    }
    public PickupDroppedItemTask(ItemTarget target, boolean freeInventoryIfFull) {
        this(new ItemTarget[] {target}, freeInventoryIfFull);
    }

    public PickupDroppedItemTask(Item item, int targetCount, boolean freeInventoryIfFull) {
        this(new ItemTarget(item, targetCount), freeInventoryIfFull);
    }

    @Override
    protected void onStart(AltoClef mod) {
        _fullCheckFailed = false;
    }

    @Override
    protected void onStop(AltoClef mod, Task interruptTask) {

    }

    @Override
    protected Task onTick(AltoClef mod) {
        if (_wanderTask.isActive() && !_wanderTask.isFinished(mod)) {
            setDebugState("Wandering after blacklisting item...");
            _progressChecker.reset();
            return _wanderTask;
        }
        if (!_progressChecker.check(mod)) {
            _progressChecker.reset();
            if (_currentDrop != null) {
                Debug.logMessage(Util.arrayToString(Util.toArray(ItemEntity.class, _blacklist), element -> element == null? "(null)" : element.getStack().getItem().getTranslationKey()));
                Debug.logMessage("Failed to pick up drop, adding to blacklist.");
                _blacklist.add(_currentDrop);
                return _wanderTask;
            }
        }
        if (_freeInventoryIfFull) {
            boolean weGood = ResourceTask.ensureInventoryFree(mod);

            if (weGood) {
                _fullCheckFailed = false;
            } else {
                if (!_fullCheckFailed) {
                    Debug.logWarning("Failed to free up inventory as no throwaway-able slot was found. Awaiting user input.");
                }
                _fullCheckFailed = true;
            }
        }

        return super.onTick(mod);
    }


    @Override
    protected boolean isEqual(Task other) {
        // Same target items
        if (other instanceof PickupDroppedItemTask) {
            PickupDroppedItemTask t = (PickupDroppedItemTask) other;
            return Util.arraysEqual(t._itemTargets, _itemTargets) && t._freeInventoryIfFull == _freeInventoryIfFull;
        }
        return false;
    }

    @Override
    protected String toDebugString() {
        StringBuilder result = new StringBuilder();
        result.append("Pickup Dropped Items: [");
        int c = 0;
        for (ItemTarget target : _itemTargets) {
            result.append(target.toString());
            if (++c != _itemTargets.length) {
                result.append(", ");
            }
        }
        result.append("]");
        return result.toString();
    }

    @Override
    protected Vec3d getPos(AltoClef mod, ItemEntity obj) {
        return obj.getPos();
    }

    @Override
    protected ItemEntity getClosestTo(AltoClef mod, Vec3d pos) {
        if (!mod.getEntityTracker().itemDropped(_itemTargets)) return null;
        return mod.getEntityTracker().getClosestItemDrop(pos, _itemTargets);
    }

    @Override
    protected Vec3d getOriginPos(AltoClef mod) {
        return mod.getPlayer().getPos();
    }

    @Override
    protected Task getGoalTask(ItemEntity obj) {
        _currentDrop = obj;
        return new GetToEntityTask(obj);
    }

    @Override
    protected boolean isValid(AltoClef mod, ItemEntity obj) {
        return obj.isAlive() && !_blacklist.contains(obj);
    }

}

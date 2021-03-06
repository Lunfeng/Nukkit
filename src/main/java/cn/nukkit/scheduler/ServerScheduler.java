package cn.nukkit.scheduler;

import cn.nukkit.Server;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.PluginException;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class ServerScheduler {
    protected PriorityBlockingQueue<TaskHandler> queue;
    protected HashMap<Long, TaskHandler> tasks;
    //todo asyncpool
    private long ids = 1;
    protected long currentTick = 0;

    Comparator<TaskHandler> comparator = new Comparator<TaskHandler>() {
        @Override
        public int compare(TaskHandler handler1, TaskHandler handler2) {
            return handler1.getNextRun() < handler2.getNextRun() ? -1 : (handler1.getNextRun() == handler2.getNextRun() ? 0 : -1);
        }
    };

    public ServerScheduler() {
        this.queue = new PriorityBlockingQueue<TaskHandler>(11, comparator);
    }

    public TaskHandler scheduleTask(Task task) {
        return this.addTask(task, -1, -1);
    }

    //todo AsyncTask PS: is this necessary?

    public TaskHandler scheduleDelayedTask(Task task, int delay) {
        return this.addTask(task, delay, -1);
    }

    public TaskHandler scheduleReaptingTask(Task task, int period) {
        return this.addTask(task, -1, period);
    }

    public TaskHandler scheduleDelayedRepeatingTask(Task task, int delay, int period) {
        return this.addTask(task, delay, period);
    }

    public void cancelTask(Long taskId) {
        if (taskId != null && this.tasks.containsKey(taskId)) {
            this.tasks.get(taskId).cancel();
            this.tasks.remove(taskId);
        }
    }

    public void cancelTask(PluginBase plugin) {
        for (Map.Entry<Long, TaskHandler> entry : this.tasks.entrySet()) {
            long taskId = entry.getKey();
            TaskHandler task = entry.getValue();
            Task ptask = task.getTask();
            if (ptask instanceof PluginTask && ((PluginTask) ptask).getOwner().equals(plugin)) {
                task.cancel();
                this.tasks.remove(taskId);
            }
        }
    }

    public void cancelAllTasks() {
        for (Map.Entry<Long, TaskHandler> entry : this.tasks.entrySet()) {
            entry.getValue().cancel();
        }
        this.tasks = new HashMap<Long, TaskHandler>();
        this.queue = new PriorityBlockingQueue<TaskHandler>(11, comparator);
        this.ids = 1;
    }

    public boolean isQueued(long taskId) {
        return this.tasks.containsKey(taskId);
    }

    private TaskHandler addTask(Task task, int delay, int period) {
        if (task instanceof PluginTask) {
            if (((PluginTask) task).getOwner() != null) {
                throw new PluginException("PluginTask " + task.getClass().getName() + " 的拥有者为NULL");
            } else if (!((PluginTask) task).getOwner().isEnabled()) {
                throw new PluginException("插件 " + ((PluginTask) task).getOwner().getName() + " 企图在禁用状态下注册PluginTask");
            }
        }
        //todo CallBackTask??
        if (delay <= 0) {
            delay = -1;
        }

        if (period <= -1) {
            delay = -1;
        } else if (period < 1) {
            period = 1;
        }

        return this.handle(new TaskHandler(
                task.getClass().getName(), task, this.nextId(), delay, period
        ));
    }

    private TaskHandler handle(TaskHandler handler) {
        long nextRun;
        if (handler.isDelayed()) {
            nextRun = this.currentTick + handler.getDelay();
        } else {
            nextRun = this.currentTick;
        }
        handler.setNextRun(nextRun);
        this.tasks.put(handler.getTaskId(), handler);
        this.queue.add(handler);
        return handler;
    }

    public void mainThreadHeartbeat(long currentTick) {
        this.currentTick = currentTick;
        while (this.isReady(this.currentTick)) {
            TaskHandler task = this.queue.poll();
            if (task.isCancelled()) {
                this.tasks.remove(task.getTaskId());
                continue;
            } else {
                //todo: Timing
                try {
                    task.run(this.currentTick);
                } catch (Exception e) {
                    Server.getInstance().getLogger().critical("无法启动task " + task.getTaskName() + ": " + e.getMessage());
                    Server.getInstance().getLogger().logException(e);
                }
            }
            if (task.isRepeating()) {
                task.setNextRun(this.currentTick + task.getPeriod());
                this.queue.add(task);
            } else {
                task.remove();
                this.tasks.remove(task.getTaskId());
            }
        }
    }

    private boolean isReady(long currentTick) {
        return !this.tasks.isEmpty() && this.queue.peek().getNextRun() <= currentTick;
    }

    private long nextId() {
        this.ids++;
        return this.ids;
    }

}

package reqorder;

import java.util.Timer;
import java.util.TimerTask;

class TimeOutTask extends TimerTask
{
    private final Thread t;
    private final Timer timer;

    TimeOutTask(Thread t, Timer timer)
    {
        this.t = t;
        this.timer = timer;
    }

    @Override
    public void run()
    {
        if (t != null && t.isAlive())
        {
            t.interrupt();
            timer.cancel();
        }
    }
}

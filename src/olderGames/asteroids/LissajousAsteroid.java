package olderGames.asteroids;

import math.Vector2d;

import static olderGames.asteroids.Constants.*;

/**
 * Created by simonlucas on 06/06/15.
 */


public class LissajousAsteroid extends Asteroid {


    public LissajousAsteroid(AsteroidsGameState game, Vector2d s, Vector2d v, int index) {
        // this is a bit broken, does not vary the size depending on the index
        super(s, v, index, 10);
    }

    public boolean wrappable() {
        return true;
    }

    double t = rand.nextDouble();

    public void update() {
        t += 0.005;
        setPosition(t, s);
        // System.out.println("t = " + t);
    }

    void setPosition(double t, Vector2d s) {
        double x = Math.sin(t + Math.PI / 6) +
                Math.sin(2.0 * t + Math.PI) + 0.5 * Math.sin(4 * t);
        double y = 1.0 * Math.sin(1.0 * t) + Math.sin(Math.PI / 5 + 2 * t) +
                0.5 * Math.sin(3 * t + Math.PI / 8);
        s.setX(0.5 * x * width + width / 2);
        s.setY(0.5 * y * height + height / 2);
    }
}




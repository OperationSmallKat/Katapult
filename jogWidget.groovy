
import com.neuronrobotics.bowlerstudio.BowlerStudio
import com.neuronrobotics.bowlerstudio.assets.ConfigurationDatabase
import com.neuronrobotics.bowlerstudio.creature.EngineeringUnitsSliderWidget
import com.neuronrobotics.bowlerstudio.creature.TransformWidget
import com.neuronrobotics.sdk.addons.gamepad.BowlerJInputDevice
import com.neuronrobotics.sdk.addons.gamepad.IGameControlEvent
import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR
import com.neuronrobotics.sdk.common.DeviceManager
import com.neuronrobotics.sdk.util.ThreadUtil

import javafx.event.ActionEvent

TransformWidget widget = args[0]

println widget
ArrayList<String> local = [
	"Dragon",
	"X-Box",
	"Game",
	"XBOX",
	"Microsoft",
	"GPD"
]
ArrayList<String> gameControllerNames = ConfigurationDatabase.getObject("katapult", "gameControllerNames", local)
gameControllerNames.addAll(local)

BowlerJInputDevice g
List<String> alreadyConnected = DeviceManager.listConnectedDevice(BowlerJInputDevice.class)
if(alreadyConnected.size()==0) {
	try {
		//Check if the device already exists in the device Manager
		g=DeviceManager.getSpecificDevice("gamepad",{
			def t = new BowlerJInputDevice(gameControllerNames); //
			t.connect(); // Connect to it.
			return t
		})
	}catch(Throwable t) {
		println "No game controllers found, Searched:\n"+gameControllerNames
		return;
	}
}else {
	def name =alreadyConnected.get(0)
	g=DeviceManager.getSpecificDevice(name)
	if(!gameControllerNames.contains(name)) {
		def newList=[]
		newList.addAll(gameControllerNames)
		newList.add(name)
		ConfigurationDatabase.setObject("katapult", "gameControllerNames", newList)
		ConfigurationDatabase.save();
	}
}

println "Starting Jog loop with \n"+g

double lud=0;
double lrl=0;
double rud=0;
double rlr=0;
double trig=0;
double zoom=0;
boolean rotation=false;
boolean run=true;
boolean dirty=false;
long timeSinceLastControl=0;
double threshhold = 0.3

long timeOfLast = System.currentTimeMillis()
widget.setMode("X for Translation, Y for Rotation");
IGameControlEvent listener = { name, value->
	switch(name) {
		case "arrow-up-down":
			zoom=value;
			break;
		case "l-joy-up-down":
			lud=-value;
			break;
		case "l-joy-left-right":
			lrl=-value;
			break;
		case "r-joy-up-down":
			rud=-value;
			break;
		case "r-joy-left-right":
			rlr=-value;
			break;
		case "y-mode":
			if(value>0.1) {
				rotation=true
				widget.setMode("Rotation");
			}
			break;
		case "x-mode":
			if(value>0.1) {
				rotation=false
				widget.setMode("Translation");
			}
			break;
		case "a-mode":
			if(value>0.5) {
				println "Exiting because A button pressed"
				run=false
			}
			break;
		case"r-trig-button":
			trig=value
			break;
		case"l-trig-button":
			trig=-value;
			break;
		default:
			println "Unmapped "+name+" "+value
			return;
	}
	//println "Dirty ("+name+")"
	if(Math.abs(value)>threshhold) {
		dirty=true;
		timeSinceLastControl=System.currentTimeMillis();
	}
}
g.clearListeners()
// gamepad is a BowlerJInputDevice
g.addListeners(listener);
public enum Quadrent {
	first,second,third,fourth
}
Quadrent getQuad(double angle) {
	if(angle>45 && angle<135)
		return Quadrent.first;
	if(angle>135 ||angle < (-135))
		return Quadrent.second;
	if(angle>-135&& angle<-45)
		return Quadrent.third;
	if(angle>-45&&angle<45)
		return Quadrent.fourth;
	throw new RuntimeException("Impossible nummber! "+angle);
}
double 	QuadrentToAngle(Quadrent q) {
	switch(q) {
		case Quadrent.first:
		return 90;
		case Quadrent.second:
		return 180;
		case Quadrent.third:
		return -90;
		case Quadrent.fourth:
		return 0;
	}
}
try{
	RotationNR getCamerFrameGetRotation;
	double currentRotZ ;
	Quadrent quad ;
	while(!Thread.interrupted() && run ){
		Thread.sleep(100)
		getCamerFrameGetRotation = BowlerStudio.getCamerFrame().getRotation()
		def toDegrees = Math.toDegrees(getCamerFrameGetRotation.getRotationAzimuth())
		quad = getQuad(toDegrees)
		currentRotZ = QuadrentToAngle(quad);
		if(Math.abs(rlr)>0.1||Math.abs(rud)>0.1) {
			double currentEle = Math.toDegrees(getCamerFrameGetRotation.getRotationTilt());
			double elSet= rud
			def stepRotation = widget.rotationIncrement*5
			
			if((currentEle>(180) || currentEle<-90 )&& elSet>0) {
				elSet=0;
			}
			if((currentEle<0&& currentEle>-90)&& elSet<0) {
				elSet=0;
			}
			quad = getQuad(currentRotZ)
			//println "Current rotation = "+currentRotZ+" ele = "+currentEle+" elSet "+ elSet
			//println "Quadtent: "+quad
			RotationNR rot = new RotationNR(elSet*3,rlr*3,0);
			TransformNR tf =new TransformNR(0,0,0,rot)

			BowlerStudio.moveCamera(tf)
		}
		if(Math.abs(zoom)>0) {
			BowlerStudio.zoomCamera(zoom*50)
		}

		TransformNR orentationOffset = new TransformNR(0,0,0,new RotationNR(0,currentRotZ-90,0))
		TransformNR frame = BowlerStudio.getTargetFrame() ;
		TransformNR frameOffset = new TransformNR(0,0,0,frame.getRotation())
		TransformNR stateUnitVector = new TransformNR();
		
		double bound =0.5;
		if(dirty) {
			if(System.currentTimeMillis()-timeSinceLastControl>500) {
				dirty=false;
				TransformNR current = widget.getCurrent();

				double azUpdate2 = Math.toDegrees(current.getRotation().getRotationAzimuth())
				double  tiltUpdate2 = Math.toDegrees(current.getRotation().getRotationTilt())
				double eleUpdate2 = Math.toDegrees(current.getRotation().getRotationElevation())
				azUpdate2 = roundToNearist(azUpdate2,widget.rotationIncrement)
				tiltUpdate2 = roundToNearist(tiltUpdate2,widget.rotationIncrement)
				eleUpdate2 = roundToNearist(eleUpdate2,widget.rotationIncrement)
				RotationNR bounded
				try {
					bounded = new RotationNR(tiltUpdate2,azUpdate2,eleUpdate2)
				}catch(java.lang.RuntimeException ex) {
					ex.printStackTrace();
					bounded = new RotationNR(0,0,89.999)
				}
				//println "Bounding rotations "+System.currentTimeMillis()
				double incement = widget.linearIncrement;
				
				current=new TransformNR(
				roundToNearist(current.getX(),incement),
				roundToNearist(current.getY(),incement),
				roundToNearist(current.getZ(),incement));
				current.setRotation(bounded)
				widget.updatePose(current)
				widget.handle(null);
			}
		}
		if(rotation) {
			TransformNR stateUnitVectorTmp = new TransformNR();
			
			if(lud>threshhold)
				stateUnitVectorTmp.translateY(1);
			if(lud<-threshhold)
				stateUnitVectorTmp.translateY(-1);
			if(lrl>threshhold)
				stateUnitVectorTmp.translateZ(1);
			if(lrl<-threshhold)
				stateUnitVectorTmp.translateZ(-1);
			if(trig>threshhold)
				stateUnitVectorTmp.translateX(1);
			if(trig<-threshhold)
				stateUnitVectorTmp.translateX(-1);
			stateUnitVector= orentationOffset.times(stateUnitVectorTmp)
			double incement = widget.rotationIncrement;
			double eleUpdate = 0;
			double tiltUpdate = 0;
			double azUpdate = 0;
			boolean updateTrig = false;
			if(stateUnitVector.getX()<-bound) {
				tiltUpdate+=incement
				updateTrig=true
			}
			if(stateUnitVector.getX()>bound) {
				tiltUpdate-=incement
				updateTrig=true
				
			}
			if(stateUnitVector.getY()<-bound) {
				eleUpdate-=incement;
				updateTrig=true
				
			}
			if(stateUnitVector.getY()>bound) {
				eleUpdate+=incement
				updateTrig=true
				
			}
			if(stateUnitVector.getZ()<-bound) { 
				azUpdate-=incement
				updateTrig=true
				
			}
			if(stateUnitVector.getZ()>bound) {
				azUpdate+=incement
				updateTrig=true
				
			}
			if(!updateTrig)
				continue;
			TransformNR update = new TransformNR(0,0,0,new RotationNR(0,0,eleUpdate))
			update = update.times(new TransformNR(0,0,0,new RotationNR(tiltUpdate,0,0)))
			update = update.times(new TransformNR(0,0,0,new RotationNR(0,azUpdate,0)))
			TransformNR current = widget.getCurrent();
			TransformNR currentRotation = new TransformNR(0,0,0,current.getRotation())
			TransformNR updatedRotation = current.times( 
				currentRotation.inverse().times( 
						frameOffset.inverse().times(update).times(frameOffset)
						.times(currentRotation)
					)
				)
			double azUpdate2 = Math.toDegrees(updatedRotation.getRotation().getRotationAzimuth())
			double  tiltUpdate2 = Math.toDegrees(updatedRotation.getRotation().getRotationTilt())
			double eleUpdate2 = Math.toDegrees(updatedRotation.getRotation().getRotationElevation())
//			azUpdate2 = roundToNearist(azUpdate2,1)
//			tiltUpdate2 = roundToNearist(tiltUpdate2,1)
//			eleUpdate2 = roundToNearist(eleUpdate2,1)
			RotationNR bounded
			try {
				bounded = new RotationNR(tiltUpdate2,azUpdate2,eleUpdate2)
			}catch(java.lang.RuntimeException ex) {
				ex.printStackTrace();
				bounded = new RotationNR(0,0,89.999)
			}
			current.setRotation(updatedRotation.getRotation())
			//println"\n\n"
			//println update.toSimpleString()
			//println current.toSimpleString()
			widget.updatePose(current)
			widget.handle(null);
		}else {
			TransformNR stateUnitVectorTmp = new TransformNR();
			
			if(lud>threshhold)
				stateUnitVectorTmp.translateX(1);
			if(lud<-threshhold)
				stateUnitVectorTmp.translateX(-1);
			if(lrl>threshhold)
				stateUnitVectorTmp.translateY(1);
			if(lrl<-threshhold)
				stateUnitVectorTmp.translateY(-1);
			if(trig>threshhold)
				stateUnitVectorTmp.translateZ(1);
			if(trig<-threshhold)
				stateUnitVectorTmp.translateZ(-1);
			double incement = widget.linearIncrement;
			stateUnitVector= orentationOffset.times(stateUnitVectorTmp)
			stateUnitVector.setRotation(new RotationNR())
			boolean updateTrig = false;
			if(stateUnitVector.getX()>bound)
				updateTrig=true
			if(stateUnitVector.getX()<-bound)
				updateTrig=true
			if(stateUnitVector.getY()>bound)
				updateTrig=true
			if(stateUnitVector.getY()<-bound)
				updateTrig=true
			if(stateUnitVector.getZ()>bound)
				updateTrig=true
			if(stateUnitVector.getZ()<-bound)
				updateTrig=true
			if(!updateTrig)
				continue;
			stateUnitVector=new TransformNR(
				roundToNearist(stateUnitVector.getX()*incement,incement),
				roundToNearist(stateUnitVector.getY()*incement,incement),
				roundToNearist(stateUnitVector.getZ()*incement,incement))
			TransformNR current = widget.getCurrent();
			TransformNR currentRotation = new TransformNR(0,0,0,current.getRotation())
			TransformNR tf= current.times(	
									currentRotation.inverse().times(
										frameOffset.inverse().times(stateUnitVector).times(frameOffset)						
									.times(currentRotation)
								)
							)
			//println "\n\n"
			//println tf.toSimpleString()
			widget.updatePose(tf)
			widget.handle(null);
		}
	}
}catch(Throwable t){
	//if(!InterruptedException.class.isInstance(t))
		t.printStackTrace()
}
double roundToNearist(double incoiming, double modulo) {
	return modulo*(Math.round(incoiming/modulo))
}
void wrapintPlusOne(EngineeringUnitsSliderWidget wid,double increment) {
	if(wid.getValue()+increment>wid.setpoint.getMax()) {
		wid.setValue(wid.setpoint.getMin());
	}else
		wid.jogPlusOne()
}
void wrapintMinusOne(EngineeringUnitsSliderWidget wid,double increment) {
	if(wid.getValue()-increment<wid.setpoint.getMin()) {
		wid.setValue(wid.setpoint.getMax());
	}else
		wid.jogMinusOne()
}
println "Clean exit from jogWidget.groovy in Katapult contains="+g.listeners.contains(listener)+" run="+run
//remove listener and exit
g.removeListeners(listener);




const MONITOR_WORKER_REQUEST_SETTINGS = {
  // canvas 画布信息轮询
  realtimeBase: {
    name: 'realtimeBase',
    sys: 'BD',
    url: null,
    // url: `/api/v1/data/mobile-robot/getRealtimeInfo?floorNumber=${payload.floorNumber}&isFirst=${payload.isFirst}&mapCode=${payload.mapCode}`,
    method: 'get',
    interval: 500,
    diffs:null,
    lastData: null,
    isOpen: false,
  },
  errorRobotListRealtime: {
    name: 'errorRobotListRealtime',
    sys: 'BD',
    url: null,
    // url: `/api/v1/data/mobile-robot/getRealtimeInfo?floorNumber=${payload.floorNumber}&isFirst=${payload.isFirst}&mapCode=${payload.mapCode}`,
    method: 'get',
    interval: 10000,
    diffs:null,
    lastData: null,
    isOpen: false,
  },
  // 任务信息
  missions: {
    name: 'missions',
    sys: 'BD',
    url: `/api/v1/data/mobile-robot/executingMissions`,
    body:[],
    method: 'post',
    interval: 2000,
    beforeRequest(requestBody){ //请求前拦截
      return requestBody.length > 0;
    },
    diffs:null,
    lastData: null,
    isOpen: true,
  },
  // 容器信息
  container: {
    name: 'container',
    sys: 'BD',
    url: null,
    // url: `/api/v1/data/mobile-robot/monitor/container?containerCode=eee1`,
    body:[],
    method: 'get',
    interval: 2000,
    beforeRequest(requestBody, nowSettings){ //请求前拦截
      return nowSettings.url !== null
    },
    diffs:null,
    lastData: null,
    isOpen: true,
  },
  //  机器人
  robots: {
    name: 'robots',
    sys: 'BD',
    url: `/api/v1/data/mobile-robot/robotinfos`,
    body:[],
    method: 'post',
    interval: 2000,
    beforeRequest(requestBody){ //请求前拦截
      return requestBody.length > 0;
    },
    // beforeResponse(response, requestBody, lastData){ // 对返回值进行处理，优先级低于diffs,
    //   console.log('response',response)
    //   return response
    // },
    beforeResponse(response, requestBody, lastData){
      function isLastMessageWithThisTypeIsTheSameValue( message, messagesArr, messageType){
        let lastMess = null
        for (let i = 0; i < messagesArr.length; i++) {
          const item = messagesArr[i]
          if(lastMess == null && item.type === messageType) {
            lastMess = item
          }
        }
        if (lastMess === null) return false
        return lastMess.val === message
      }
      let lastMessages = {}
      let robotMissionCheck = new Map() // robotId: {isChange, oldMissionCode, newMissionCode}
      if(lastData !== null) {
        lastData.robotMessageArr.map(lastRobot=>{
          lastMessages[lastRobot.robotId] = lastRobot.runTimeMessage
        })
        robotMissionCheck = lastData.robotMissionCheck
      }
      const robotMessageArr =  response.map(robot => {
        let runTimeMessage = lastMessages[robot.robotId] || []
        // 添加碰撞信息到运行信息
        if(robot.collisionType && robot.collisionType !== 0) {
          const collisionMessage = `${robot.collisionType}&&${robot.obstacleCoordinateX}&&${robot.obstacleCoordinateY}&&${robot.obstacleCoordinateZ}`
          const isSame = isLastMessageWithThisTypeIsTheSameValue(collisionMessage,runTimeMessage, "collisionMessage")
          if(!isSame){
            runTimeMessage.unshift({
              time: new Date(),
              val: collisionMessage,
              type: "collisionMessage"
            })
          }
        }
        // 其他信息推到运行信息
        const jobinfoMsg = robot.jobId ? `[ID:${robot.jobId}]` : '';
        const warninginfoMsg = robot.warningMessage ? ` [${robot.warningMessage}]` : '';
        const controllerwarninginfoMsg = robot.controllerWarningMessage ? ` [${robot.controllerWarningMessage}]` : '';

        const itgMsg = `${jobinfoMsg}${warninginfoMsg}${controllerwarninginfoMsg}`
        if(itgMsg.length > 0) {
          const isSame = isLastMessageWithThisTypeIsTheSameValue(itgMsg,runTimeMessage, "controllerWarningMessage")
          if(!isSame){
            runTimeMessage.unshift({
              time: new Date(),
              val: itgMsg,
              type: "controllerWarningMessage"
            })
          }
        }


        if(runTimeMessage.length > 10) {
          runTimeMessage.pop()
        }
        robot.runTimeMessage = runTimeMessage



        // 计算是否mission变更
        // let robotMissionCheck = new Map // robotId: {isChange, oldMissionCode, newMissionCode}
        if(lastData !== null){
          const oldData = robotMissionCheck.get(robot.robotId)
          const oldMissionCode = oldData ? oldData.newMissionCode :null
          const newMissionCode = robot.missionCode ? robot.missionCode : null
          robotMissionCheck.set(robot.robotId, {
            oldMissionCode: oldMissionCode,
            newMissionCode: newMissionCode,
            isChange: newMissionCode === null ? false : oldMissionCode !== newMissionCode
          })
        } else {
          robotMissionCheck.set(robot.robotId, {
            oldMissionCode: null,
            newMissionCode: robot.missionCode,
            isChange: false
          })
        }
        return robot
      })
      return {robotMessageArr,robotMissionCheck}
    },
    // diffs:['totalMileage'], function(old, new){返回值为true才会推送}
    diffs:null,
    lastData: null,
    isOpen: true,
  },
  //  机器人
  robotscompare: {
    name: 'robotscompare',
    sys: 'BD',
    url: `/api/v1/data/mobile-robot/robotinfos`,
    body:[],
    method: 'post',
    interval: 2000,
    beforeRequest(requestBody){ //请求前拦截
      return requestBody.length > 0;
    },
    // beforeResponse(response, requestBody, lastData){ // 对返回值进行处理，优先级低于diffs,
    //   console.log('response',response)
    //   return response
    // },
    beforeResponse(response, requestBody, lastData){
      const robotMessageArr =  response.map(robot => {
        return robot
      })
      return {robotMessageArr}
    },
    // diffs:['totalMileage'], function(old, new){返回值为true才会推送}
    diffs:null,
    lastData: null,
    isOpen: true,
  },
  // 充电点信息
  chargeEnterPointsStatus: {
    name: 'chargeEnterPointsStatus',
    sys: 'BD',
    url: null,
    // url: '/api/v1/data/node-function/select/${mapCode}/${floorNumber}/${functionType}',
    method: 'get',
    interval: 30000,
    diffs:null,
    lastData: null,
    isOpen: false,
  },
  workflowjob: {
    name: 'workflowjob',
    sys: 'WCS',
    url: `/api/job/queryJobListForDashboard`,
    body: {},
    method: 'post',
    interval: 10000,
    // diffs:['totalMileage'],
    diffs:null,
    lastData: null,
    isOpen: false,
  },
  workflowjobdetail: {
    name: 'workflowjobdetail',
    sys: 'WCS',
    url: `/api/job/queryJobDetailForDashBoardByJobId`,
    body: null,
    method: 'get',
    interval: 10000,
    // diffs:['totalMileage'],
    diffs:null,
    lastData: null,
    isOpen: false,
  },
  workflowpinnedjob: {
    name: 'workflowpinnedjob',
    sys: 'WCS',
    url: `/api/job/queryJobListForDashboardByJobIds`,
    body: null,
    method: 'get',
    interval: 10000,
    // diffs:['totalMileage'],
    diffs:null,
    lastData: null,
    isOpen: false,
  },
  robotlist: {
    name: 'robotlist',
    sys: 'BD',
    url: `/api/v1/data/mobile-robot/robotAndMission`,
    body: {},
    method: 'post',
    interval: 5000,
    // diffs:['totalMileage'],
    diffs:null,
    lastData: null,
    isOpen: false,
  },
  statbd: {
    name: 'statbd',
    sys: 'BD',
    url: `/api/v1/data/global/monitor/robot/statistics`,
    body: {},
    method: 'get',
    interval: 5000,
    // diffs:['totalMileage'],
    diffs:null,
    lastData: null,
    isOpen: false,
  },
  statwcs: {
    name: 'statwcs',
    sys: 'WCS',
    url: `/api/job/queryExecutingJobCountForDashboard`,
    body: {},
    method: 'get',
    interval: 5000,
    // diffs:['totalMileage'],
    diffs:null,
    lastData: null,
    isOpen: false,
  },
  firealarmsignal: {
    name: 'firealarmsignal',
    sys: 'BD',
    url: `/api/v1/data/fire-alarm/all`,
    method: 'get',
    interval: 6000,
    // diffs:['totalMileage'],
    diffs:null,
    lastData: null,
    isOpen: false,
  },
  hoverinfodata: {
    name: 'hoverinfodata',
    sys: 'BD',
    url: `/api/v1/data/mobile-robot/getRobotJobCode`,
    body: {},
    method: 'post',
    interval: 10000,
    diffs:null,
    lastData: null,
    isOpen: false,
  },
}
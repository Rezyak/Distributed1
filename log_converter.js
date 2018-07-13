const logs = './logs/'
const fs = require('fs')
const os = require("os")

try{
    fs.readdirSync(logs).forEach(file => {
        let file_path = logs+file
        let file_path_test = logs+'test.log'
       
        let lines = fs.readFileSync(file_path, 'utf-8')
            .split('\n')
            .filter(Boolean)
        for (let line of lines){
            fs.appendFileSync(file_path_test, line+os.EOL) 
        }
    })
}catch(e){
    console.log('\n',e,'\n')
}
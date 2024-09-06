const { resolve } = require("path");
const fs = require("fs");
const path = require("path");
const { execSync, exec } = require('child_process');
const https = require("https")
module.exports = {
    getJavaExec: () => {
        return new Promise((resolve, reject) => {
            const basePath = path.join(process.cwd(), 'java_runtime')
            resolve(path.join(basePath, fs.readdirSync(basePath)[0], 'bin', 'java'))
        })
    },
    installJava: () => {
        return new Promise((resolve, reject) => {
            if (!fs.existsSync(process.cwd() + "/java_runtime")) {
                fs.mkdirSync(process.cwd() + "/java_runtime")
            
                const url = "https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.tar.gz";
                const downloadPath = process.cwd() + '/jdk.tar.gz';
    
                const file = fs.createWriteStream(downloadPath);
                console.log("> Downloading JDK")
                const request = https.get(url, function(response) {
                    response.pipe(file);
                });
            
                file.on('finish', function() {
                    file.close();
                    try {
                        console.log("> Extract jdk zip")
                        execSync(`tar -zxvf ${downloadPath} -C ${process.cwd()}/java_runtime`);
                        console.log("> Remove jdk zip")
                        execSync(`rm ${downloadPath}`)
                        console.log("> JDK succesfully installed on /java_runtime")
                        resolve({ status: "SUCCESFULLY_INSTALLED" })
                        // runEvents();
                    } catch (error) {                        
                        resolve({ status: "ERROR", err: error })
                    }
                });
            } else {
                resolve({ status: "ALREADY_INSTALLED" })
                // runEvents();
            }
        })
    }
}
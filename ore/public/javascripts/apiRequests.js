//=====> EXTERNAL CONSTANTS

let csrf = null;
let isLoggedIn = false;

//=====> HELPER FUNCTIONS

function apiV2Request(url, method, data, isRetry) {
    return getApiSession().then(function (session) {
        return new Promise(function (resolve, reject) {
            if(!data) {
                data = {};
            }

            const isFormData = data instanceof FormData;
            let allData;
            if(isFormData) {
                data.append('csrfToken', csrf);
                allData = data;
            }
            else {
                allData = Object.assign(data, {csrfToken: csrf});
            }

            $.ajax({
                url: '/api/v2/' + url,
                method: method,
                dataType: 'json',
                contentType: isFormData ? false : 'application/json',
                data: isFormData ? allData : JSON.stringify(allData),
                processData: isFormData ? false : undefined,
                headers: {'Authorization': 'ApiSession ' + session}
            }).done(function (data) {
                resolve(data);
            }).fail(function (xhr) {
                if (xhr.responseJSON && (xhr.responseJSON.error === 'Api session expired' || xhr.responseJSON.error === 'Invalid session')) {
                    if (isRetry === true) {
                        reject('Api session expired twice')
                    } else {
                        invalidateApiSession();
                        apiV2Request(url, method, data, true).then(function (data) {
                            resolve(data);
                        }).catch(function (error) {
                            reject(error);
                        });
                    }
                } else {
                    reject(xhr.statusText)
                }
            })
        })
    });
}

function getApiSession() {
    return new Promise(function (resolve, reject) {
        let session;
        if (isLoggedIn) {
            session = localStorage.getItem('api_session');
            if (session === null) {
                return $.ajax({
                    url: '/api/v2/authenticate/user',
                    method: 'POST',
                    dataType: 'json',
                    data: {
                        csrfToken: csrf
                    }
                }).done(function (data) {
                    if (data.type !== 'user') {
                        reject('Expected user session from user authentication');
                    } else {
                        localStorage.setItem('api_session', data.session);
                        resolve(data.session);
                    }
                }).fail(function (xhr) {
                    reject(xhr.statusText)
                })
            } else {
                resolve(session);
            }
        } else {
            session = localStorage.getItem('public_api_session');
            if (session === null) {
                $.ajax({
                    url: '/api/v2/authenticate',
                    method: 'POST',
                    dataType: 'json',
                    data: {
                        csrfToken: csrf
                    }
                }).done(function (data) {
                    if (data.type !== 'public') {
                        reject('Expected public session from public authentication')
                    } else {
                        localStorage.setItem('public_api_session', data.session);
                        resolve(data.session);
                    }
                }).fail(function (xhr) {
                    reject(xhr.statusText)
                })
            } else {
                resolve(session);
            }
        }
    });
}

function invalidateApiSession() {
    if (isLoggedIn) {
        localStorage.removeItem('api_session')
    }
    else {
        localStorage.removeItem('public_api_session')
    }
}
function validateMeta(pluginId, name, baseUrl, owner, slug) {
    checkId(pluginId, name, baseUrl, owner, slug)
}

function tooltip(selector, title) {
    $(selector).tooltip({
        placement: 'right',
        title: title
    });
}

function removeSpinner(selector) {
    $(selector).removeClass('fa-spinner fa-spin');
}

function success(selector) {
    removeSpinner(selector);
    $(selector).addClass('fa-check-circle');
}

function failed(selector, message) {
    removeSpinner(selector);
    $(selector).addClass('fa-times-circle');
    tooltip(selector, message);
}

function checkId(pluginId, name, baseUrl, owner, slug) {
    $.ajax({
        url: baseUrl + '/api/project?pluginId=' + pluginId,
        statusCode: {
            404: function() {
                success('.id-status');
                checkName(name, true, baseUrl, owner, slug);
            },
            200: function() {
                failed('.id-status', 'That plugin ID is not available!');
                checkName(name, false, baseUrl, owner, slug);
            }
        }
    })
}

function checkName(name, idSuccess, baseUrl, owner, slug) {
    if (name.length > 25) {
        failed('.name-status', 'This name is too long. Please rename your project to something under 25 characters.');
        updateContinueButton(idSuccess, false);
        return;
    }

    $.ajax({
        url: baseUrl + '/' + owner + '/' + slug,
        statusCode: {
            404: function() {
                success('.name-status');
                updateContinueButton(idSuccess, true);
            },
            200: function() {
                failed('.name-status', 'You already have a project of this name!');
                updateContinueButton(idSuccess, false);
            }
        }
    });
}

function updateContinueButton(idSuccess, nameSuccess) {
    if (idSuccess && nameSuccess) {
        $('.continue-btn').prop('disabled', false);
    }
};
